# Deploy Emerald

## New installation

Use a Docker-capable Linux host and a domain pointed to it. Open inbound TCP 80/443; do not expose PostgreSQL or API ports.

1. Put this project on the host and open its directory.
2. Build: `docker build -t emerald-api .`
3. Generate the password hash: `docker run --rm -it emerald-api --hash-password`. Enter a unique password of at least 16 characters; save it in your password manager.
4. Copy `.env.example` to `.env`. Set the real domain, long random database password, admin username and generated hash. Keep the hash in single quotes so Compose preserves dollar signs. Run `chmod 600 .env`.
5. Run `docker compose config --quiet`, then `docker compose up -d --build`.
6. Check `https://YOUR_DOMAIN/api/actuator/health` returns `{"status":"UP"}`. Open `/admin.html` and sign in.
7. Submit a public enquiry, read/update it in admin, save a CMS field and upload an image. Verify the website and reset temporary edits.
8. Run `./deploy/backup.sh`, then `./deploy/verify-restore.sh backups/TIMESTAMP/database.dump`. Copy backups to encrypted off-host storage and arrange daily scheduling.

Caddy obtains and renews public TLS certificates when DNS and ports are correct. Do not bypass public certificate errors. Only the bundled Caddy edge may reach the API: it overwrites X-Real-IP for the limiter. Adding another proxy requires an explicit trusted-proxy configuration.

## Existing database: preserve enquiries

Automatic Flyway baseline is disabled. Never enable Hibernate update or drop production tables to force startup.

1. Take `pg_dump -Fc` and restore it to a separate staging database.
2. Compare staging with `backend/src/main/resources/db/migration/V1__baseline.sql`. Resolve structural differences without discarding records.
3. Use Flyway CLI against staging to run `baseline -baselineVersion=1`, supplying credentials securely rather than in shell history.
4. Start the new app against staging. V2 adds notes/status, versions and audit history. Verify existing records, edits and a backup restoration.
5. Stop the old app, take a fresh production backup, and repeat the proven baseline/migration steps on production.

## Operations

Monitor `/api/actuator/health` externally. Review `docker compose logs --tail=100 api edge db` without enabling credential/request-body logs.

Password rotation: generate a new hash, update `.env`, run `docker compose up -d --force-recreate api`. Existing sessions expire. Limits are five contact attempts/IP/minute and ten sign-in attempts/IP/minute; restart resets counters.

The backup helper pauses API writes to snapshot database and images together, then restarts it. The restore test creates a new disposable database only. For a real rollback, retain failed volumes, restore a verified database dump and matching media into NEW volumes, then run the prior app version against that restored copy. Media files belong to UID/GID 10001. Never downgrade code against an incompatible migrated schema.

Enquiries are stored; email notification is not configured. The admin reply link opens your email client and does not send automatically.

No hosting account/domain or production database was supplied in this task. Actual container startup, public DNS/certificate issuance, resource sizing, existing-data migration and off-host backup scheduling must be checked on the selected host. No live customer records were changed here.
