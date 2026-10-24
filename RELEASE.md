# Emerald review corrections — 24 October 2026

The original 63870b1 release is not approved. Its upload evidence is withdrawn: media source was omitted by broad directory exclusions. Archived reports are not valid evidence for that commit.

Corrections: restore image upload/read routes, anchor runtime media exclusions at the project root, fix menu resize/Escape/focus behavior, remove FAQ height limits, make portfolio descriptions keyboard/touch accessible, normalize CMS allowlist line endings, and keep the loading overlay hidden until JavaScript runs.

Added regression coverage for uploads/security/re-encoding, line endings and public browser interactions. Verification is performed on a clean Git export; see evidence/ for new results and their source identity. Historical reports are isolated in archive/unverified-evidence-63870b1/.

Public hosting, Docker runtime, broad physical-device testing and independent security/accessibility assessments remain unverified. Do not deploy until Aziz reviews the corrected commit and the actual deployment checks in DEPLOY.md pass.
