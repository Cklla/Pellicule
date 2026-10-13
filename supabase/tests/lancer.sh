#!/usr/bin/env bash
# Rejoue les migrations puis les tests d'isolation sur un Postgres jetable (Docker).
# Usage : supabase/tests/lancer.sh
set -euo pipefail

cd "$(dirname "$0")/../.."
conteneur="pellicule-test-rls-$$"
trap 'docker rm -f "$conteneur" >/dev/null 2>&1 || true' EXIT

docker run -d --name "$conteneur" -e POSTGRES_PASSWORD=test postgres:18 >/dev/null
until docker exec "$conteneur" pg_isready -U postgres >/dev/null 2>&1; do sleep 1; done
sleep 2

executer() {
    docker exec -i "$conteneur" psql -U postgres -v ON_ERROR_STOP=1 -q <"$1"
}

executer supabase/tests/00_simulation_supabase.sql
for migration in supabase/migrations/*.sql; do
    executer "$migration"
done
executer supabase/tests/rls.sql
