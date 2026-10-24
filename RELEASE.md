# Emerald review corrections — 24 October 2026

The original 63870b1 release is not approved. Its upload evidence is withdrawn: media source was omitted by broad directory exclusions. Historical reports in archive/unverified-evidence-63870b1/ are not valid evidence for that commit.

## Fixed findings
- Added authenticated PNG/JPEG upload and public retrieval endpoints, size/dimension validation, generated names and re-encoding. Root-only runtime media exclusions now preserve Java media source.
- Mobile menu closes on desktop resize and Escape, restores scrolling/focus and excludes closed links from keyboard navigation.
- Expanded FAQ answers size to content instead of 200px.
- Portfolio descriptions reveal on keyboard focus and remain visible on touch devices.
- Content-key parsing handles CRLF/LF and trims lines; Git attributes normalize text.
- Loader is hidden by default and survives blocked session storage.
- Withdrawn mismatched evidence; fresh results are bound to a clean Git export.

## Verification
Tested code commit: 77d5135f173524c78a0c881fe9aa587f881155f0. See evidence/review-provenance.json for individual source hashes. Later evidence/documentation commits do not alter this tested implementation.

- Java 21 Maven clean verify: 31 tests, zero failures/errors.
- Separate CRLF-formatted copy: 31 tests, zero failures/errors. This is a Linux simulation, not a native Windows execution.
- Real PostgreSQL 16.9 and production Spring profile via Caddy local HTTPS: 29 API checks.
- Chromium full workflow: 17 checks including image upload and mobile visitor enquiry followed up in admin.
- Public review regression suite: 10 checks covering menu, FAQ, portfolio, no JavaScript and blocked storage.
- Restart/HTTPS: 5 checks, including persisted enquiry notes/status, CMS assignment and byte-identical uploaded image after restart.
- UI static audit: zero findings. JavaScript syntax passes. MediaController is present in the exported source and built executable JAR.

## Remaining gates
Aziz must retest the correction. Docker execution and public hosting are not verified here. Native Windows execution is configured in CI but not yet verified. No new full database/image disaster-recovery test was performed in this correction run. Physical-device performance and exhaustive security/accessibility remain unverified. Follow DEPLOY.md before launch.
