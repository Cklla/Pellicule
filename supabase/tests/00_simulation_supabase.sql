-- Reproduit le strict minimum de Supabase pour jouer les migrations sur un Postgres nu :
-- rôles, schéma `auth`, `auth.uid()` et publication Realtime. Inutile sur un vrai projet
-- Supabase, où tout cela existe déjà.

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
alter default privileges in schema public grant all on tables to anon, authenticated;

create publication supabase_realtime;
