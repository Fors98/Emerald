#!/bin/sh
set -eu
umask 077
out="${1:-backups/$(date -u +%Y%m%dT%H%M%SZ)}"
mkdir -p "$out"
docker compose stop api
trap 'docker compose start api >/dev/null' EXIT INT TERM
docker compose exec -T db pg_dump -U emerald -d emerald -Fc > "$out/database.dump"
docker compose run --rm --no-deps --entrypoint tar api -C /app/media -czf - . > "$out/media.tar.gz"
test -s "$out/database.dump"
test -s "$out/media.tar.gz"
sha256sum "$out/database.dump" "$out/media.tar.gz" > "$out/SHA256SUMS"
printf 'Backup saved to %s. Copy it to encrypted off-server storage.\n' "$out"
