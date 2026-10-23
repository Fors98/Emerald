#!/bin/sh
# Read-only checks; no admin secret required. Run while local backend is running.
set -u
BASE="${1:-http://127.0.0.1:8084/api}"
echo "PUBLIC CONTENT (expect 200):"
curl -sS -o /dev/null -w '%{http_code}\n' "$BASE/content"
echo "ADMIN ANONYMOUS (expect 401):"
curl -sS -o /dev/null -w '%{http_code}\n' "$BASE/admin/contacts"
echo "CONTACT COUNT (expect 55 on original DB):"
psql -d emerald_db -Atc 'SELECT COUNT(*) FROM contacts;'
