-- Schéma initial de Pellicule : suivi des médias et des épisodes vus.
--
-- Chaque ligne appartient à un compte (`user_id`) et n'est visible que par lui (RLS). Les
-- contraintes CHECK reprennent les bornes qu'imposaient auparavant les règles Firestore, pour
-- qu'un compte authentifié ne puisse pas remplir la base de données arbitrairement grosses ou
-- mal formées.

create table public.media (
    -- UUID généré par l'application (déjà celui de Room) : conservé tel quel à la migration, et
    -- le rejeu d'une écriture en attente reste idempotent.
    id uuid primary key,
    user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    title text not null check (char_length(title) between 1 and 300),
    type text not null check (type in ('FILM', 'SERIE', 'ANIME')),
    status text not null check (status in ('A_VOIR', 'EN_COURS', 'VU')),
    tmdb_id bigint check (tmdb_id between 0 and 9999999999),
    release_year integer check (release_year between 1850 and 2999),
    poster_url text check (char_length(poster_url) <= 2000),
    jellyfin_id text check (char_length(jellyfin_id) <= 200),
    rating integer check (rating between 1 and 5),
    -- Epoch en millisecondes, comme dans l'application ; borne haute large (an 3000).
    watched_at bigint check (watched_at between 0 and 32503680000000),
    created_at timestamptz not null default now(),
    -- Cible de la clé étrangère composite de `watched_episode`.
    unique (id, user_id)
);

create table public.watched_episode (
    media_id uuid not null,
    season integer not null check (season between 0 and 9999),
    episode integer not null check (episode between 0 and 99999),
    user_id uuid not null default auth.uid(),
    primary key (media_id, season, episode),
    -- Clé composite : un épisode ne peut pas pointer vers le média d'un autre compte, même si
    -- son auteur en connaît l'identifiant.
    foreign key (media_id, user_id) references public.media (id, user_id) on delete cascade
);

-- Les politiques RLS filtrent sur `user_id` ; la clé primaire couvre déjà `media_id`.
create index media_user_id_idx on public.media (user_id);
create index watched_episode_user_id_idx on public.watched_episode (user_id);

alter table public.media enable row level security;
alter table public.watched_episode enable row level security;

-- `(select auth.uid())` plutôt que `auth.uid()` nu : évalué une fois par requête, pas par ligne.
create policy "media : lecture de ses lignes" on public.media
    for select to authenticated
    using (user_id = (select auth.uid()));

create policy "media : insertion de ses lignes" on public.media
    for insert to authenticated
    with check (user_id = (select auth.uid()));

create policy "media : modification de ses lignes" on public.media
    for update to authenticated
    using (user_id = (select auth.uid()))
    with check (user_id = (select auth.uid()));

create policy "media : suppression de ses lignes" on public.media
    for delete to authenticated
    using (user_id = (select auth.uid()));

create policy "episodes vus : lecture de ses lignes" on public.watched_episode
    for select to authenticated
    using (user_id = (select auth.uid()));

create policy "episodes vus : insertion de ses lignes" on public.watched_episode
    for insert to authenticated
    with check (user_id = (select auth.uid()));

-- Pas de politique de modification : un épisode vu s'ajoute ou se retire, il ne change jamais.
create policy "episodes vus : suppression de ses lignes" on public.watched_episode
    for delete to authenticated
    using (user_id = (select auth.uid()));

-- Aucun accès sans compte connecté.
revoke all on public.media from anon;
revoke all on public.watched_episode from anon;

-- Realtime sert de simple signal « quelque chose a changé » : l'application refait ensuite un
-- chargement complet. Les suppressions ne sont pas filtrées par le RLS côté Realtime, mais ne
-- transportent que la clé primaire (identité de réplication par défaut).
alter publication supabase_realtime add table public.media, public.watched_episode;
