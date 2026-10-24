# Emerald: pre-deployment review for Aziz

Correction branch: fix/aziz-review-findings, based on release commit 63870b1. The correction can be fast-forwarded into release/emerald-website-review.

## Scope
Public agency website plus private admin, Java 21 / Spring Boot 3.5.16 / PostgreSQL. Review RELEASE.md and DEPLOY.md before setup. This branch is not a production deployment.

## Start safely
Use a new disposable PostgreSQL database and new media volume. Do not point these acceptance tests at production: they write content and enquiries and exercise rate limits. No credentials are included. Generate your own administrator password/hash using DEPLOY.md. Configure .env locally; never commit it.

## Required release checks
1. Run mvn -f backend/pom.xml clean verify (Java 21).
2. Run docker compose --env-file .env.example config --quiet.
3. Build both images and start the full Compose stack using real local .env settings and a test hostname with working HTTPS. These container execution checks remain unverified in the supplied evidence.
4. Verify health, public pages and admin login; anonymous admin requests must fail.
5. Submit an enquiry; read it in admin; save a private note and change its status.
6. Edit website text and confirm the public page updates; reset it.
7. Upload/assign/reset an image. Restart the API and confirm data and images persist.
8. Verify failed-save drafts, session expiry recovery and stale edit conflicts.
9. Run API then browser acceptance scripts as described in tests.md, against disposable data only.
10. Run backup.sh and verify-restore.sh. Also prove recovery of matching image files into isolated storage; database restore alone is insufficient.
11. Inspect mobile and desktop layouts, keyboard interaction, browser console and network failures.
12. Check real business claims, contact details and privacy wording before publication.

## Known limitations
One administrator, one API instance, no automatic enquiry email alerts, no 2FA or self-service password recovery, no automatic data/image cleanup. Portfolio entries are concepts. Broad device coverage, load testing and independent security/accessibility assessments are not established.

## Report findings
For each failure include: commit SHA, environment, reproduction steps, expected result, actual result, severity and redacted logs/screenshots. Never include passwords, cookies or customer information. Mark PASS / FAIL / NOT TESTED for each required check. Do not merge or deploy until blocking findings are resolved.

Saved evidence in evidence/ is historical from the controlled test environment, not the result of your review. CI configuration is supplied; a successful remote run is still required.

## Retest the reported defects
Read RELEASE.md and evidence/review-provenance.json. Ignore archived evidence for 63870b1. Run `node tests/review-browser.cjs` with Playwright installed; it starts its own static server and tests public interactions using a CMS fixture. Run the live API/browser scripts against the real backend as well. Confirm the media Java source is tracked and present in the built JAR. Repeat the full visitor → enquiry → admin notes/status → restart → retained record and image journey.
