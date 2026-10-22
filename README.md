# Emerald

 Emerald is a digital studio website with a separate Spring Boot backend for contact submissions.

## Project structure

 - `index.html` — main website
- `styles.css` — site styling
- `script.js` — front-end interactions, form wiring and API-driven sections
- `api-client.js` — shared API base URL, offline seed content and fetch wrapper (`window.Emerald`)
- `admin.html`, `admin.js`, `admin.css` — staff dashboard (sign-in, messages, subscribers, content, stats)
- `privacy.html` — privacy information
- `404.html` — not-found page
- `assets/extracted/` — local image assets
- `api.yaml` — OpenAPI contract (v0.2.0)
- `CHANGES.md` — what changed in the v0.2 merge and why
- `tests.md` — API verification results
- `backend/` — Spring Boot backend

## Frontend

The website uses HTML, CSS and vanilla JavaScript.

The site works without a backend: `index.html` contains the full static content. When the API answers, the Services, Work, Testimonials, FAQ and Stats sections are replaced with live data (`GET /api/services|projects|testimonials|faqs|stats`). The contact form posts to `POST /api/contact` and the footer form to `POST /api/newsletter/subscribe`.

The API base defaults to `/api` (same origin). To point at another origin during development add `<meta name="emerald-api-base" content="http://localhost:8080/api">` to the page head; the backend then needs CORS enabled for that origin.

`admin.html` is the staff dashboard. If no backend answers it runs in a clearly-labelled demo mode on sample data.

## Backend

The backend uses Java 21, Spring Boot 3.5.6, Spring Web, Spring Validation, Spring Data JPA, PostgreSQL and Maven.

Implemented so far: `POST /api/contact` and the admin authentication endpoints `POST /api/auth/login`, `/api/auth/refresh`, `/api/auth/logout` and `GET /api/auth/me`. The full contract (20 operations) is in `api.yaml`; the remaining controllers are still to be built against it.

Build fields:
- `name` — required
- `email`  — required
- `message` – required, minimum 10 characters
- `company` – optional
- `projectType` — optional

A 201 response is returned on success. Validation errors return 400.

## Database

The local PostgreSQL database is `emerald_db` on `localhost:5432`.

The application currently uses Hibernate schema updates with:

`spring.jpa.hibernate.ddl-auto=update`

There is currently no Flyway or Liquibase migration structure in this repository.

## Running the backend

```
cd backend
mvn spring-boot:run
```

The API runs locally at:

http://localhost:8080/api

## Testing

```
cd backend
mvn clean test
```

API verification is documented in `tests.md` . The current tests cover successful contact submission, validation errors, missing required fields and database persistence.

## API contract

`api.yaml` (v0.2.0) is the reference for the public and admin API. Change the contract first, get it reviewed, then build or change the controller.


## Admin authentication

Package `com.emerald.backend.auth` (Spring Security, Argon2id, short-lived JWT access tokens, rotating refresh tokens in an HttpOnly cookie).

Required environment variables (the app refuses to start without the first two):

```
EMERALD_AUTH_JWT_SECRET=<openssl rand -base64 48>
EMERALD_AUTH_ALLOWED_ORIGINS=https://your-site.example
# first start only, then remove:
EMERALD_AUTH_BOOTSTRAP_EMAIL=you@example.com
EMERALD_AUTH_BOOTSTRAP_PASSWORD=<at least 14 characters>
```

Local development over plain HTTP: copy `src/main/resources/application-local.properties.example` to `application-local.properties` and run `mvn spring-boot:run -Dspring-boot.run.profiles=local`.

Auth tests run on in-memory H2 (`mvn clean test`).
