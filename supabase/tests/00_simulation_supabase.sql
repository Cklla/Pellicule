-- Reproduit le strict minimum de Supabase pour jouer les migrations sur un Postgres nu :
-- rôles, schéma `auth`, `auth.uid()` et publication Realtime. Inutile sur un vrai projet
-- Supabase, où tout cela existe déjà.
--
-- Aucun droit par défaut sur les tables du schéma public : un projet Supabase récent n'en accorde
-- pas aux rôles de l'API, chaque table doit recevoir ses GRANT dans les migrations.

create role anon nologin;
create role authenticated nologin;
create role supabase_auth_admin nologin;

create schema auth;
create table auth.users (id uuid primary key default gen_random_uuid(), email text);

create function auth.uid() returns uuid
language sql stable
as $$
    select nullif(current_setting('request.jwt.claims', true)::jsonb ->> 'sub', '')::uuid
$$;

grant usage on schema public, auth to anon, authenticated;
grant execute on function auth.uid() to anon, authenticated;
grant select on auth.users to authenticated;

create publication supabase_realtime;
