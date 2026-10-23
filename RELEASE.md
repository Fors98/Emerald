# Emerald 1.0.0 — verification, 23 October 2026

Implemented: public enquiry form, authenticated administrator sessions, editable website text, image uploads/assignment/reset, enquiry status/private notes, paginated enquiry and audit views, version conflict protection, database migrations, HTTPS reverse proxy, container configuration, backup/restore helpers and CI.

Verified on the recovered source:
- Maven clean verify: 19 regression/security tests, zero failures or errors.
- Real PostgreSQL 16 API acceptance: 29 passing checks.
- Chromium browser acceptance: 16 passing checks, including mobile layout, keyboard interaction, failed-save draft preservation and expired-session recovery.
- Persistence: 3 passing checks covering API restart, database backup restoration and retained private notes.
- HTTPS: 5 passing cookie/header checks through Caddy with the production Spring profile.
- Strict UI source audit: zero findings. Mobile admin screenshot visually inspected.
- Caddy production configuration and Docker Compose configuration validate.

Machine-readable results are in evidence/. These are bounded acceptance checks, not a claim of exhaustive security or accessibility certification. The tests used disposable databases; no live records were touched.

## Deployment status

Source and deployment configuration are prepared. The app is not live. This environment has no Docker daemon, so the container image builds and Compose runtime still need execution on the target host. CI includes those builds. Public DNS, certificate issuance, host sizing, monitoring, off-host backup scheduling and any existing-database migration remain host-specific release checks. Follow DEPLOY.md.

The delivered scope is the Emerald website and administrative backend. Email notifications, multi-user roles, external CRM integrations and AI agent orchestration are not implemented. The public portfolio is labeled as concept work, and unsupported testimonials/contact claims were removed.
