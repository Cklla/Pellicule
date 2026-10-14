#!/usr/bin/env bash
# Vérifie la procédure de restauration de la sauvegarde (voir supabase/RESTAURATION.md) sur deux
# Postgres jetables (Docker) : un « ancien projet » dont on tire un dump avec les options du job
# de sauvegarde, et un « nouveau projet » où les comptes ont été recréés avec d'autres identifiants.
# Contrôle que le dump brut est refusé (clé étrangère vers auth.users), que le remappage de
# `user_id` le rend restaurable, et que la restauration est complète.
# Le projet est partagé avec Cartouche : le dump couvre aussi le schéma `cartouche`, dont les
# migrations vivent dans le dépôt Cartouche (CARTOUCHE_MIGRATIONS, par défaut le dépôt voisin).
# Usage : [CARTOUCHE_MIGRATIONS=/chemin/vers/Cartouche/supabase/migrations] supabase/tests/restaurer_sauvegarde.sh
set -euo pipefail

cd "$(dirname "$0")/../.."
source_c="pellicule-test-sauvegarde-source-$$"
cible_c="pellicule-test-sauvegarde-cible-$$"
tmp="$(mktemp -d)"
cartouche_migrations="${CARTOUCHE_MIGRATIONS:-$(cd .. && pwd)/Cartouche/supabase/migrations}"
if ! ls "$cartouche_migrations"/*.sql >/dev/null 2>&1; then
    echo "ÉCHEC : migrations de Cartouche introuvables dans $cartouche_migrations (variable CARTOUCHE_MIGRATIONS)" >&2
    exit 1
fi
trap 'docker rm -f "$source_c" "$cible_c" >/dev/null 2>&1 || true; rm -rf "$tmp"' EXIT

ancien_a='aaaaaaaa-0000-0000-0000-000000000001'
ancien_b='bbbbbbbb-0000-0000-0000-000000000002'
nouveau_a='cccccccc-0000-0000-0000-000000000003'
nouveau_b='dddddddd-0000-0000-0000-000000000004'

demarrer() {
    docker run -d --name "$1" -e POSTGRES_PASSWORD=test postgres:18 >/dev/null
    until docker exec "$1" pg_isready -U postgres >/dev/null 2>&1; do sleep 1; done
    sleep 2
    for fichier in supabase/tests/00_simulation_supabase.sql supabase/migrations/*.sql "$cartouche_migrations"/*.sql; do
        docker exec -i "$1" psql -U postgres -v ON_ERROR_STOP=1 -q <"$fichier"
    done
}
requete() { docker exec -i "$1" psql -U postgres -v ON_ERROR_STOP=1 -q -t -A -c "$2"; }
verifier() {
    if [ "$2" != "$3" ]; then
        echo "ÉCHEC : $1 (attendu « $3 », obtenu « $2 »)" >&2
        exit 1
    fi
}

demarrer "$source_c"
demarrer "$cible_c"

requete "$source_c" "insert into auth.users (id, email) values
    ('$ancien_a', 'a@exemple.fr'), ('$ancien_b', 'b@exemple.fr')"
requete "$source_c" "insert into public.allowed_email (email) values ('a@exemple.fr'), ('b@exemple.fr')"
requete "$source_c" "insert into public.media (id, user_id, title, type, status) values
    ('11111111-1111-4111-8111-111111111111', '$ancien_a', 'Série de A', 'SERIE', 'EN_COURS'),
    ('22222222-2222-4222-8222-222222222222', '$ancien_a', 'Film de A', 'FILM', 'VU'),
    ('33333333-3333-4333-8333-333333333333', '$ancien_b', 'Anime de B', 'ANIME', 'A_VOIR')"
requete "$source_c" "insert into public.watched_episode (media_id, user_id, season, episode) values
    ('11111111-1111-4111-8111-111111111111', '$ancien_a', 1, 1),
    ('11111111-1111-4111-8111-111111111111', '$ancien_a', 1, 1123),
    ('11111111-1111-4111-8111-111111111111', '$ancien_a', 2, 1)"

requete "$source_c" "insert into cartouche.game (id, user_id, title, platform, genre, status) values
    ('44444444-4444-4444-8444-444444444444', '$ancien_a', 'Jeu de A', 'PC', 'RPG', 'EN_COURS'),
    ('55555555-5555-4555-8555-555555555555', '$ancien_a', 'Autre jeu de A', 'Switch', 'Action', 'TERMINE'),
    ('66666666-6666-4666-8666-666666666666', '$ancien_b', 'Jeu de B', 'PS5', 'Course', 'A_FAIRE')"

# Mêmes options que le job `sauvegarde_supabase`.
docker exec "$source_c" pg_dump -U postgres --data-only --schema=public --schema=cartouche --no-owner >"$tmp/sauvegarde.sql"
test -s "$tmp/sauvegarde.sql"

# Nouveau projet : adresses autorisées à la main, comptes créés par la reconnexion Google avec
# d'autres identifiants.
requete "$cible_c" "insert into public.allowed_email (email) values ('a@exemple.fr'), ('b@exemple.fr')"
requete "$cible_c" "insert into auth.users (id, email) values
    ('$nouveau_a', 'a@exemple.fr'), ('$nouveau_b', 'b@exemple.fr')"

# Les adresses du dump entreraient en conflit avec celles saisies à la main : on les vide d'abord.
requete "$cible_c" "truncate public.allowed_email"

# Le dump brut doit être refusé : ses `user_id` ne désignent aucun compte du nouveau projet.
if docker exec -i "$cible_c" psql -U postgres -v ON_ERROR_STOP=1 -q --single-transaction \
    <"$tmp/sauvegarde.sql" >/dev/null 2>"$tmp/refus.txt"; then
    echo "ÉCHEC : le dump brut aurait dû être refusé" >&2
    exit 1
fi
grep -q "foreign key constraint" "$tmp/refus.txt" || {
    echo "ÉCHEC : refus pour une autre raison que la clé étrangère :" >&2
    cat "$tmp/refus.txt" >&2
    exit 1
}
verifier "rien n'est restauré après le refus" "$(requete "$cible_c" 'select count(*) from public.media')" 0
verifier "rien n'est restauré après le refus (cartouche)" "$(requete "$cible_c" 'select count(*) from cartouche.game')" 0

# Procédure documentée : remplacer chaque ancien identifiant de compte par le nouveau.
sed -e "s/$ancien_a/$nouveau_a/g" -e "s/$ancien_b/$nouveau_b/g" "$tmp/sauvegarde.sql" >"$tmp/remappee.sql"
docker exec -i "$cible_c" psql -U postgres -v ON_ERROR_STOP=1 -q --single-transaction <"$tmp/remappee.sql"

verifier "médias restaurés" "$(requete "$cible_c" 'select count(*) from public.media')" 3
verifier "épisodes restaurés" "$(requete "$cible_c" 'select count(*) from public.watched_episode')" 3
verifier "adresses autorisées restaurées" "$(requete "$cible_c" 'select count(*) from public.allowed_email')" 2
verifier "médias de A" \
    "$(requete "$cible_c" "select count(*) from public.media where user_id = '$nouveau_a'")" 2
verifier "médias de B" \
    "$(requete "$cible_c" "select count(*) from public.media where user_id = '$nouveau_b'")" 1
verifier "identifiants de contenu conservés" \
    "$(requete "$cible_c" "select count(*) from public.media where id in
        ('11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222',
         '33333333-3333-4333-8333-333333333333')")" 3
verifier "aucun ancien identifiant de compte" \
    "$(requete "$cible_c" "select count(*) from public.media where user_id in ('$ancien_a', '$ancien_b')")" 0
verifier "jeux restaurés" "$(requete "$cible_c" 'select count(*) from cartouche.game')" 3
verifier "jeux de A" \
    "$(requete "$cible_c" "select count(*) from cartouche.game where user_id = '$nouveau_a'")" 2
verifier "jeux de B" \
    "$(requete "$cible_c" "select count(*) from cartouche.game where user_id = '$nouveau_b'")" 1
verifier "identifiants de jeu conservés" \
    "$(requete "$cible_c" "select count(*) from cartouche.game where id in
        ('44444444-4444-4444-8444-444444444444', '55555555-5555-4555-8555-555555555555',
         '66666666-6666-4666-8666-666666666666')")" 3
verifier "aucun ancien identifiant de compte (cartouche)" \
    "$(requete "$cible_c" "select count(*) from cartouche.game where user_id in ('$ancien_a', '$ancien_b')")" 0

echo "Restauration vérifiée."
