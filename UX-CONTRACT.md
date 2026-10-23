# Emerald UI contract

Source: recovered CMS/contact app and the user's instruction to complete it for deployment. The single ADMIN authority remains, now authenticated using password/session. No billing, external sending, enquiry deletion or retention change. Follow-up status and notes are reversible organization metadata.

| Capability | Canonical owner | Source of truth | Allowed variants | Verification |
|---|---|---|---|---|
| Select/Listbox | Native HTML select | admin.js, script.js | OS popup | Browser tests |
| Form | admin.js fieldInput/run/request, public script.js | api.yaml | CMS, notes, upload, contact | API/browser tests |
| Scrollbar | admin.css and styles.css roots | DESIGN.md | High contrast | CSS inspection |
| Toast | admin.js status, public formStatus | api.yaml | Success/error | Browser tests |
| CRUD | admin.js request and confirmAction | api.yaml | Save stays, reset confirms | Full-flow tests |

Saves publish immediately. Versions prevent stale overwrites; drafts survive failed saves. Reset confirms and restores defaults. Enquiries paginate 10/page. Images are decoded/re-encoded and assigned via owned URLs. Unlinked images are retained. Unsaved drafts remain in memory, warn before navigation, and survive session reauthentication. Sign-out clears private UI. Permission source is SecurityConfig.java; lifecycle source is API validation and Flyway migrations. Public privacy copy is retained.
