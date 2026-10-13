-- Droits d'accès des tables aux rôles de l'API. Le RLS filtre les lignes, mais sans GRANT le rôle
-- `authenticated` est refusé avant même d'arriver aux politiques (SQLSTATE 42501). Un projet
-- Supabase récent n'accorde plus ces droits automatiquement aux nouvelles tables.

grant select, insert, update, delete on public.media to authenticated;

-- Un épisode vu s'ajoute ou se retire, il ne se modifie jamais (voir les politiques).
grant select, insert, delete on public.watched_episode to authenticated;
