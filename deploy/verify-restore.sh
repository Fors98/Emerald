#!/bin/sh
# Restore into a NEW disposable database only; never overwrite production.
set -eu
backup="${1:?Pass a database.dump path}"
verify_db="emerald_restore_$(date +%s)"
docker compose exec -T db createdb -U emerald "$verify_db"
trap 'docker compose exec -T db dropdb -U emerald "$verify_db" >/dev/null' EXIT INT TERM
docker compose exec -T db pg_restore -U emerald --exit-on-error --no-owner -d "$verify_db" < "$backup"
docker compose exec -T db psql -U emerald -d "$verify_db" -v ON_ERROR_STOP=1 -c 'SELECT count(*) AS contacts FROM contacts; SELECT count(*) AS content FROM site_content; SELECT count(*) AS audit_events FROM audit_events;'
printf 'Restore verified in an isolated database.\n'
