# v0.2 merge — what changed and why

## Why this merge was needed
Two people extended the same static site from different starting points:

* **Wael** (v0.1.0): image extraction, alt text, path fixes, `404.html`, `privacy.html`, the Spring Boot `ContactController`, and `api.yaml` covering `/contact` only.
* **Front-end / contract work** (v0.2): dynamic Services/Work/Testimonials/FAQ/Stats, newsletter form, admin dashboard, and an `api.yaml` covering 19 more endpoints.

Neither branch contained the other's work. Anyone building the remaining controllers would have coded against a contract that did not match the shipped front end. This merge produces one baseline: **Wael's repo + the v0.2 front end + the v0.2 contract.**

Note: the original v0.2 files were not available when this merge was made, so the v0.2 front end and the 19-endpoint list were rebuilt from the agreed scope (see "What the reviewer should double-check").

## Kept exactly from Wael
* `assets/extracted/*`, alt text, path fixes, `404.html`, `privacy.html`
* Entire `backend/` module (unchanged)
* `POST /contact` operation and its schemas in `api.yaml` (unchanged)

## Front end
| File | Change |
|---|---|
| `index.html` | Static content kept as the baseline (works without JS/backend; SEO-safe). Added container ids `servicesList`, `workGrid`, `testimonialsGrid`, `faqList`, `statsRow`; `err-company` / `err-projectType` spans; newsletter form in footer; Admin link; loads `api-client.js`. Removed the "front-end demo" note from the contact form. |
| `script.js` | Contact form now calls `POST /contact` (replaces the `setTimeout` demo) and maps `400 { errors }` onto the `#err-*` spans; newsletter form; dynamic rendering of the five sections from the API (DOM APIs + `textContent`, never `innerHTML`); scroll-spy discovers `.service-block` elements instead of a hard-coded list; FAQ accordion is event-delegated; stats count-up can re-bind after re-render. |
| `styles.css` | Newsletter form, `.form-status.is-error`, `[hidden]`, footer link wrapping. Nothing existing was changed. |
| `api-client.js` (new) | `Emerald.API_BASE`, `Emerald.SEED` (offline copy of the content), `Emerald.request()`. |
| `admin.html` / `admin.js` / `admin.css` (new) | Sign-in, overview, messages (status + delete), subscribers, CRUD for services/projects/testimonials/FAQ, stats editor. Demo mode with a visible banner if no backend answers. |

## Contract: `api.yaml` 0.1.0 → 0.2.0
Now **20 operations** = `POST /contact` (existing) + **19 new**:

| Group | Operations |
|---|---|
| Public content (5) | `GET /services`, `/projects`, `/testimonials`, `/faqs`, `/stats` |
| Newsletter (1) | `POST /newsletter/subscribe` (201 new, 200 already subscribed) |
| Auth (3) | `POST /auth/login`, `POST /auth/logout`, `GET /auth/me` |
| Admin (10) | `GET /admin/dashboard`; `GET /admin/contacts`, `PATCH`/`DELETE /admin/contacts/{id}`; `GET /admin/subscribers`, `DELETE /admin/subscribers/{id}`; `POST /admin/{collection}`, `PUT`/`DELETE /admin/{collection}/{id}` (collection = services\|projects\|testimonials\|faqs); `PUT /admin/stats` |

Conventions: list envelope `{ items }`; 400 → `{ errors: { field: msg } }`; other errors → `{ message }`; bearer JWT for `/auth/logout`, `/auth/me` and everything under `/admin`.

## Back-end work implied by the contract
* `Contact` entity needs a `status` column (`received|read|replied|archived`, default `received`).
* New entities: Service (with unique immutable `slug`), Project, Testimonial, Faq, Stat, Subscriber, admin User.
* Spring Security + JWT; rate-limit `/auth/login`, `/contact`, `/newsletter/subscribe`.
* CORS only if the front end is served from a different origin.
* Spring path matching: literal `/admin/contacts`, `/admin/subscribers`, `/admin/dashboard`, `/admin/stats` win over `/admin/{collection}`; validate `{collection}` against the four allowed values.

## Cleanup still open from the review (not part of this merge)
1. `application.properties`: remove the committed DB username; use env vars or a gitignored `application-local.properties`.
2. Replace `ddl-auto=update` with Flyway migrations before more tables exist.
3. Add MockMvc tests, or stop claiming `mvn clean test` in the README.
4. Friendlier validation messages (contract examples imply them).

## Process rules to avoid this next time
1. **One baseline.** Everyone branches from the same tagged commit; nobody edits an old copy of `index.html`, `script.js` or `api.yaml`.
2. **Contract first.** Endpoint changes go into `api.yaml` and are reviewed before any controller is written.
3. **Git, not zip files or chat sessions.** Work that exists only in a chat or a sandbox gets lost.
4. **Small, frequent merges** so branches never drift for days.
5. **No credentials in committed config.**
6. **Migrations, not auto-DDL.**
7. **Docs match reality:** don't claim tests that don't exist.

## What the reviewer should double-check
* The 19-endpoint list and field names were reconstructed. Compare against whatever the team already agreed and adjust `api.yaml` *before* controllers are built.
* Choice made: one generic `/admin/{collection}` family instead of 12 explicit paths (keeps the count at 19). Split it if the team prefers explicit controllers; schemas don't change.
* JWT in `sessionStorage` vs httpOnly cookie is a security decision for the back-end owner.
