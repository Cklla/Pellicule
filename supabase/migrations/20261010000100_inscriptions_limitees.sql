-- Fermeture des inscriptions : seules les adresses de `allowed_email` peuvent créer un compte.
--
-- Désactiver les inscriptions côté Supabase bloquerait aussi la première connexion par jeton
-- Google ; ce hook « before user created » laisse passer uniquement les adresses autorisées. Les
-- adresses ne sont pas dans le dépôt : elles s'ajoutent à la main (éditeur SQL du tableau de bord)
-- avec `insert into public.allowed_email (email) values ('adresse@exemple.fr');`, puis le hook se
-- branche dans Authentication > Hooks > Before User Created.

create table public.allowed_email (
    email text primary key check (email = lower(email))
);

-- RLS activé sans politique pour les rôles de l'API : la liste n'est lisible par personne côté
-- application. Seul le rôle du service d'authentification la consulte.
alter table public.allowed_email enable row level security;
revoke all on public.allowed_email from anon, authenticated;
grant select on public.allowed_email to supabase_auth_admin;

create policy "adresses autorisées : lecture par le service d'authentification"
    on public.allowed_email
    for select to supabase_auth_admin
    using (true);

create function public.hook_avant_creation_utilisateur(event jsonb)
returns jsonb
language plpgsql
stable
as $$
begin
    if exists (
        select 1 from public.allowed_email
        where email = lower(event -> 'user' ->> 'email')
    ) then
        return '{}'::jsonb;
    end if;

    return jsonb_build_object(
        'error', jsonb_build_object(
            'http_code', 403,
            'message', 'Les inscriptions sont fermées.'
        )
    );
end;
$$;

grant usage on schema public to supabase_auth_admin;
grant execute on function public.hook_avant_creation_utilisateur(jsonb) to supabase_auth_admin;
revoke execute on function public.hook_avant_creation_utilisateur(jsonb) from authenticated, anon, public;
