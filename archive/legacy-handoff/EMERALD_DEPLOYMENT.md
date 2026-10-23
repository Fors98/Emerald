# Emerald deployment readiness

## Current verified evidence (from Termux)
- 15/15 Maven tests passed; backend started with JPA validation; public CMS 200; unauthorized admin 401; 55 contact records.
- Authenticated content write, restart persistence, browser UX and production hosting are **not yet verified**.

## Security and operational requirements
1. Provision managed PostgreSQL with encrypted backups and tested restore. Create the required schema using reviewed SQL migrations before starting with `SPRING_PROFILES_ACTIVE=prod`. The current project has no Flyway/Liquibase migration chain.
2. Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `CORS_ALLOWED_ORIGIN`, and a randomly generated `EMERALD_ADMIN_TOKEN` (at least 32 characters) in hosting secrets. Never put secrets in Git or screenshots.
3. Serve `admin.html` and the site over HTTPS only. Route `/api` to the Spring backend. Ensure production `/api/content` and `/api/contact` work.
4. Verify authenticated PUT to `/api/admin/content/<disposable-key>`, read-back, restart persistence, and deletion. Check missing/incorrect token responses.
5. Verify frontend mobile UX, animated visuals, CMS content overrides, form submission, and admin leads on a real device.
6. Add rate limiting to public contact form, operational monitoring, and audit trail before broad public release.

## Known limitations
The admin is a shared Bearer token, not a multi-user login system. There is no media upload manager, full CRUD for services/portfolio, or database migration tool. These are **not** silently considered complete.

## Files
- `admin.html`: admin editor and leads view.
- `cms.js`: text-only public CMS overrides.
- `backend/src/main/resources/application-prod.properties`: production configuration, corrected to retain `/api` and bind the admin token.
- `verify-local.sh`: read-only local checks against an already running server.
