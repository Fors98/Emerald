# Emerald review corrections — 25 October 2026

The original 63870b1 release is not approved. Its upload evidence is withdrawn: media source was omitted by broad directory exclusions. Historical reports in archive/unverified-evidence-63870b1/ are not valid evidence for that commit.

## Fixed findings
- Added authenticated PNG/JPEG upload and public retrieval endpoints, size/dimension validation, generated names and re-encoding. Root-only runtime media exclusions now preserve Java media source.
- Mobile menu closes on desktop resize and Escape, restores scrolling/focus and excludes closed links from keyboard navigation.
- Expanded FAQ answers size to content instead of 200px.
- Portfolio descriptions reveal on keyboard focus and remain visible on touch devices.
- Content-key parsing handles CRLF/LF and trims lines; Git attributes normalize text.
- Loader is hidden by default and survives blocked session storage.
- Withdrawn mismatched evidence; fresh results are bound to a clean Git export.

## Independent review corrections
- Navigation links focus their destination; short mobile menus can scroll.
- No-JavaScript content and FAQ answers remain readable; the contact button stays disabled until handlers initialize.
- Long portfolio descriptions expand the card without clipping.
- Upload input streams close explicitly; truncated files and audit-failure cleanup have regression coverage.

## Verification
Tested code commit: abf68736dcd3bd48e8ebafc7bb01388de9a15de9. Later evidence commits do not alter these tested sources. Individual hashes are in evidence/review-provenance.json.

- Fresh Git export, Java 21 Maven clean verify: 33 tests, zero failures/errors.
- Chromium public regression suite with mocked CMS: 13 checks passed.
- Previous results for 77d5135 are explicitly retained under evidence/previous-77d5135/: 29 real API checks, 17 full browser checks and 5 restart/HTTPS checks, plus CRLF simulation. These have not been rerun against the latest code and are not current-release acceptance evidence.

## Remaining gates
Aziz must retest the correction, including the complete real-backend visitor/admin/upload/restart journey on this exact revision. Docker execution and public hosting are not verified here. Native Windows execution is configured in CI but not yet verified. No new full database/image disaster-recovery test was performed in this correction run. Physical-device performance and exhaustive security/accessibility remain unverified. Follow DEPLOY.md before launch.
