# Emerald integrated release candidate

## Scope
- Original animated website retained; content overrides use safe `textContent`.
- Public `GET /api/content`, public `POST /api/contact`.
- Admin Bearer-token-only `GET/PUT/DELETE /api/admin/content`, `GET /api/admin/contacts`.
- Admin page at `admin.html` (must be hosted over HTTPS). Token is held only in page memory.

## Required deployment configuration
- `EMERALD_ADMIN_TOKEN`: randomly generated 32+ character secret. Never commit it or send it in chat.
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` for PostgreSQL.
- `JPA_DDL_AUTO=validate` **after** creating/verifying schema through a controlled migration.
- `CORS_ALLOWED_ORIGIN`: exact frontend origin for cross-origin contact submissions.
- `SERVER_PORT`: hosting-assigned port.
- Backend routes are under `/api`. Frontend should proxy `/api` to the backend or set `window.EMERALD_API_BASE` before `script.js` and `cms.js`.

## Limitations / release gates
- **NOT certified production-ready.** Build and tests must run in Termux/CI; inspect full HTTP security and deployment.
- Rate limiting, request logging, content audit history, media upload storage, and full service/project CRUD are not yet included.
- Admin uses a shared bearer secret, not multi-user identity/roles. Rotate on compromise.
- Public content values are rendered as text, not HTML; hero formatting is simplified if edited.
- Current local `ddl-auto=update` is for development only; production must use validated schema and migrations.
- Existing database backup must be retained; test restoration before production migrations.
- Admin page must never be served over plain HTTP on the public internet.
