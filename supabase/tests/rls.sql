-- Isolation entre comptes : A ne voit ni ne modifie les lignes de B, et ne peut pas rattacher
-- d'épisode au média de B. Chaque vérification lève une exception en cas d'écart.

insert into auth.users (id, email) values
    ('aaaaaaaa-0000-0000-0000-000000000001', 'a@exemple.fr'),
    ('bbbbbbbb-0000-0000-0000-000000000002', 'b@exemple.fr');

create function pg_temp.connecte_comme(uid uuid) returns void
language plpgsql as $$
begin
    perform set_config('request.jwt.claims', json_build_object('sub', uid)::text, true);
    set local role authenticated;
end;
$$;

create function pg_temp.refuse(requete text, motif text) returns void
language plpgsql as $$
begin
    begin
        execute requete;
    exception when others then
        return;
    end;
    raise exception 'Requête acceptée alors qu''elle devait être refusée (%) : %', motif, requete;
end;
$$;

-- A crée un média et un épisode vu ; user_id vient de auth.uid() par défaut.
begin;
select pg_temp.connecte_comme('aaaaaaaa-0000-0000-0000-000000000001');
insert into public.media (id, title, type, status)
    values ('11111111-1111-1111-1111-111111111111', 'Film de A', 'FILM', 'A_VOIR');
insert into public.watched_episode (media_id, season, episode)
    values ('11111111-1111-1111-1111-111111111111', 1, 1);
do $$ begin
    assert (select user_id from public.media) = 'aaaaaaaa-0000-0000-0000-000000000001', 'user_id par défaut';
end $$;
commit;

-- B ne voit rien, ne modifie rien, ne supprime rien.
begin;
select pg_temp.connecte_comme('bbbbbbbb-0000-0000-0000-000000000002');
do $$ begin
    assert (select count(*) from public.media) = 0, 'B voit un média de A';
    assert (select count(*) from public.watched_episode) = 0, 'B voit un épisode de A';
end $$;
update public.media set title = 'piraté';
delete from public.media;
delete from public.watched_episode;
select pg_temp.refuse(
    $q$insert into public.media (id, user_id, title, type, status)
       values (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000001', 'x', 'FILM', 'VU')$q$,
    'insertion au nom de A');
select pg_temp.refuse(
    $q$insert into public.watched_episode (media_id, season, episode)
       values ('11111111-1111-1111-1111-111111111111', 1, 2)$q$,
    'épisode sur le média de A, user_id de B');
select pg_temp.refuse(
    $q$insert into public.watched_episode (media_id, user_id, season, episode)
       values ('11111111-1111-1111-1111-111111111111', 'aaaaaaaa-0000-0000-0000-000000000001', 1, 2)$q$,
    'épisode sur le média de A, user_id de A');
select pg_temp.refuse(
    $q$insert into public.media (id, title, type, status)
       values ('11111111-1111-1111-1111-111111111111', 'écrase', 'FILM', 'VU')
       on conflict (id) do update set title = excluded.title$q$,
    'upsert sur l''identifiant de A');
commit;

-- Rien n'a bougé pour A.
begin;
select pg_temp.connecte_comme('aaaaaaaa-0000-0000-0000-000000000001');
do $$ begin
    assert (select title from public.media) = 'Film de A', 'le média de A a été modifié';
    assert (select count(*) from public.watched_episode) = 1, 'les épisodes de A ont changé';
end $$;
-- A ne peut pas céder une ligne à B.
select pg_temp.refuse(
    $q$update public.media set user_id = 'bbbbbbbb-0000-0000-0000-000000000002'$q$,
    'changement de propriétaire');
commit;

-- Contraintes CHECK (reprise des anciennes règles Firestore).
begin;
select pg_temp.connecte_comme('aaaaaaaa-0000-0000-0000-000000000001');
select pg_temp.refuse($q$insert into public.media (id, title, type, status) values (gen_random_uuid(), '', 'FILM', 'VU')$q$, 'titre vide');
select pg_temp.refuse($q$insert into public.media (id, title, type, status) values (gen_random_uuid(), repeat('x', 301), 'FILM', 'VU')$q$, 'titre trop long');
select pg_temp.refuse($q$insert into public.media (id, title, type, status) values (gen_random_uuid(), 'x', 'JEU', 'VU')$q$, 'type inconnu');
select pg_temp.refuse($q$insert into public.media (id, title, type, status) values (gen_random_uuid(), 'x', 'FILM', 'ABANDON')$q$, 'statut inconnu');
select pg_temp.refuse($q$insert into public.media (id, title, type, status, rating) values (gen_random_uuid(), 'x', 'FILM', 'VU', 6)$q$, 'note hors bornes');
select pg_temp.refuse($q$insert into public.media (id, title, type, status, release_year) values (gen_random_uuid(), 'x', 'FILM', 'VU', 1700)$q$, 'année hors bornes');
select pg_temp.refuse($q$insert into public.media (id, title, type, status, poster_url) values (gen_random_uuid(), 'x', 'FILM', 'VU', repeat('x', 2001))$q$, 'affiche trop longue');
select pg_temp.refuse($q$insert into public.media (id, title, type, status, watched_at) values (gen_random_uuid(), 'x', 'FILM', 'VU', -1)$q$, 'date de visionnage négative');
-- Champs optionnels absents, valeurs limites acceptées.
insert into public.media (id, title, type, status, tmdb_id, release_year, rating, watched_at)
    values (gen_random_uuid(), 'limite', 'ANIME', 'VU', 9999999999, 2999, 5, 32503680000000);
-- Un épisode peut être inséré deux fois avec on conflict (rejeu idempotent).
insert into public.watched_episode (media_id, season, episode)
    values ('11111111-1111-1111-1111-111111111111', 1, 1) on conflict do nothing;
commit;

-- Suppression en cascade du média : ses épisodes partent avec.
begin;
select pg_temp.connecte_comme('aaaaaaaa-0000-0000-0000-000000000001');
delete from public.media where id = '11111111-1111-1111-1111-111111111111';
do $$ begin
    assert (select count(*) from public.watched_episode) = 0, 'épisodes orphelins après suppression du média';
end $$;
commit;

-- Sans compte connecté, rien n'est lisible ni écrivable.
begin;
set local role anon;
select pg_temp.refuse('select * from public.media', 'lecture anonyme');
select pg_temp.refuse($q$insert into public.media (id, user_id, title, type, status) values (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000001', 'x', 'FILM', 'VU')$q$, 'insertion anonyme');
commit;

-- La liste des adresses autorisées n'est visible d'aucun compte de l'application.
begin;
select pg_temp.connecte_comme('aaaaaaaa-0000-0000-0000-000000000001');
select pg_temp.refuse('select * from public.allowed_email', 'lecture de la liste des adresses');
select pg_temp.refuse($q$select public.hook_avant_creation_utilisateur('{}'::jsonb)$q$, 'exécution du hook par un compte');
commit;

-- Hook d'inscription : seule une adresse autorisée passe, sans tenir compte de la casse.
insert into public.allowed_email (email) values ('a@exemple.fr');
do $$ begin
    assert public.hook_avant_creation_utilisateur('{"user": {"email": "A@Exemple.fr"}}') = '{}'::jsonb,
        'adresse autorisée refusée';
    assert public.hook_avant_creation_utilisateur('{"user": {"email": "intrus@exemple.fr"}}') ? 'error',
        'adresse inconnue acceptée';
    assert public.hook_avant_creation_utilisateur('{"user": {}}') ? 'error', 'adresse absente acceptée';
end $$;

select 'OK : tous les tests RLS passent' as resultat;
