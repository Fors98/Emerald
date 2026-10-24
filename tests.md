# Verification

Run Java 21 and Maven: `mvn -f backend/pom.xml clean verify`.

For integration tests, start the application against a disposable PostgreSQL database with administrator username `admin` and a BCrypt hash of a temporary password. Serve the public files and proxy `/api` to the backend on the same origin. Set `EMERALD_TEST_PASSWORD` to that password and `EMERALD_TEST_BASE` to the API URL. Run `python3 tests/live_api.py`. This creates enquiries, content and media and exercises rate limits. Never run against production.

Then install Playwright in your test environment and its Chromium browser. Set `EMERALD_WEB_BASE` to the web origin and run `node tests/browser.cjs`. `CHROME_PATH` can select an existing Chromium executable. Run API tests first: browser tests expect their enquiry. Test scripts replace evidence results.

For local test TLS only, Python supports `EMERALD_TEST_CA` pointing to the local CA certificate; the browser accepts `EMERALD_LOCAL_TLS=1`. Do not use certificate bypass for a real public host. Production cookie checks require HTTPS and the prod profile.

Saved results and verification limits are described in RELEASE.md. CI runs unit/security tests, JavaScript syntax checks, Compose configuration validation and both Docker builds. Browser and API acceptance require their disposable runtime and are separate from CI.

## Review regressions
`node tests/review-browser.cjs` runs the public menu/FAQ/portfolio/loader regressions with a temporary static server and mocked CMS content. Install Playwright and Chromium first. The Maven suite now discovers production media routes and tests PNG/JPEG uploads, retrieval, authorization, CSRF, re-encoding and limits. ContentKeysTest covers CRLF/LF. CI includes Linux and Windows backend jobs; configured is not the same as executed.
