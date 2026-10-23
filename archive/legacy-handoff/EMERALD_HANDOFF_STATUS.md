# Emerald — evidence-based handoff status

## Delivered in this candidate
- Original visual assets, CSS, animation JavaScript and contact form retained.
- Text CMS fields now cover hero lines, section headings, five service titles/descriptions, six portfolio cards, three testimonials and six FAQs.
- Hero emphasized word and FAQ plus icon remain in the DOM during CMS updates.
- Admin Bearer-token protected content edit/delete and leads listing, with PostgreSQL persistence.
- Production config retains `/api` and requires admin token.

## Verified externally on user Termux for previous candidate
- 15/15 Maven tests passed.
- Live public GET 200; unauthorized admin 401; PostgreSQL contacts count 55.
- Spring Boot startup succeeded using JPA schema validation.

## Not verified for THIS modified candidate
- Maven tests and real-browser end-to-end testing; must be rerun after changes.
- Authorized write, restart persistence, and admin UX on actual device.

## BLOCKERS before team says deploy
1. Admin access is a shared Bearer token, not a user login with sessions, rotation and audit history.
2. No schema migration tooling, contact rate limiting, backup-restore proof or production observability.
3. Example portfolio projects/testimonials need truthful review before publication; do not present fictional work or endorsements as real.
4. Production TLS, CORS, hosting environment, frontend routing and media delivery not tested.
5. Site has text editing only, not a full media management or structural content CRUD system.

**Release decision: NOT CLEARED FOR DEPLOYMENT.**

## Additional hardening in this build
- Production profile now refuses startup if the admin token is missing.
- Admin connect rejects blank tokens before making network requests.
- Maven regression tests have NOT been executed for this revision; no deployment clearance.
