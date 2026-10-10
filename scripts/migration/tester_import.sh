#!/usr/bin/env bash
# Joue le SQL généré par l'export sur un Postgres jetable (Docker) avec le vrai schéma : import,
# rejeu sans doublon, refus d'un compte inconnu, refus (et retour arrière) si un identifiant
# appartient à un autre compte.
# Usage : scripts/migration/tester_import.sh
set -euo pipefail

racine="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$racine"
conteneur="pellicule-test-import-$$"
tmp="$(mktemp -d)"
trap 'docker rm -f "$conteneur" >/dev/null 2>&1 || true; rm -rf "$tmp"' EXIT

docker run -d --name "$conteneur" -e POSTGRES_PASSWORD=test postgres:18 >/dev/null
until docker exec "$conteneur" pg_isready -U postgres >/dev/null 2>&1; do sleep 1; done
sleep 2

psql_fichier() { docker exec -i "$conteneur" psql -U postgres -v ON_ERROR_STOP=1 -q -t -A <"$1"; }
psql_requete() { docker exec -i "$conteneur" psql -U postgres -v ON_ERROR_STOP=1 -q -t -A -c "$1"; }

psql_fichier supabase/tests/00_simulation_supabase.sql
for migration in supabase/migrations/*.sql; do psql_fichier "$migration"; done
psql_requete "insert into auth.users (id, email) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'ami@exemple.fr'),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'autre@exemple.fr')"

cat >"$tmp/documents.json" <<'JSON'
{
  "11111111-1111-4111-8111-111111111111": {"title": "L'Attaque des Titans", "type": "ANIME", "status": "EN_COURS",
    "tmdbId": 1429, "watchedEpisodes": ["1:1", "1:2", "1:1123", "2:1"]},
  "22222222-2222-4222-8222-222222222222": {"title": "Dune", "type": "FILM", "status": "VU", "rating": 5,
    "watchedAt": 1700000000000},
  "33333333-3333-4333-8333-333333333333": {"title": "Sans épisode", "type": "SERIE", "status": "A_VOIR",
    "watchedEpisodes": []}
}
JSON

verifier() { # $1 = attendu, $2 = requête
    local obtenu
    obtenu="$(psql_requete "$2")"
    if [ "$obtenu" != "$1" ]; then echo "ÉCHEC : '$2' donne '$obtenu', attendu '$1'" >&2; exit 1; fi
}

python3 scripts/migration/export_firestore.py --json "$tmp/documents.json" \
    --email ami@exemple.fr --sortie "$tmp/import.sql" >/dev/null

# Premier import, puis rejeu : mêmes comptages, aucun doublon.
for passage in 1 2; do
    sortie="$(psql_fichier "$tmp/import.sql" | tail -n 1)"
    verifier "3" "select count(*) from public.media where user_id = 'aaaaaaaa-0000-0000-0000-000000000001'"
    verifier "4" "select count(*) from public.watched_episode where user_id = 'aaaaaaaa-0000-0000-0000-000000000001'"
    echo "passage $passage : $sortie"
done
verifier "1123" "select episode from public.watched_episode where season = 1 and episode > 100"
verifier "L'Attaque des Titans" "select title from public.media where tmdb_id = 1429"

# Compte inconnu : exception, rien d'écrit.
python3 scripts/migration/export_firestore.py --json "$tmp/documents.json" \
    --email inconnu@exemple.fr --sortie "$tmp/inconnu.sql" >/dev/null
if psql_fichier "$tmp/inconnu.sql" >/dev/null 2>&1; then echo "ÉCHEC : compte inconnu accepté" >&2; exit 1; fi

# Identifiant déjà pris par un autre compte : exception et retour arrière complet.
python3 scripts/migration/export_firestore.py --json "$tmp/documents.json" \
    --email autre@exemple.fr --sortie "$tmp/conflit.sql" >/dev/null
if psql_fichier "$tmp/conflit.sql" >/dev/null 2>&1; then echo "ÉCHEC : conflit d'identifiant accepté" >&2; exit 1; fi
verifier "0" "select count(*) from public.media where user_id = 'bbbbbbbb-0000-0000-0000-000000000002'"
verifier "3" "select count(*) from public.media"

echo "Import Firestore -> Supabase : OK"
