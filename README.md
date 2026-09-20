# Emerald

 Emerald is a digital studio website with a separate Spring Boot backend for contact submissions.

## Project structure

 - `index.html` — main website
- `styles.css` — site styling
- `script.js` —frontend interactions and validation
- `privacy.html` — privacy information
- `404.html` — not-found page
- `assets/extracted/` — local image assets
- `api.yaml` — OpenAPI contract
- `tests.md` — API verification results
- `backend/` — Spring Boot backend

## Frontend

The website uses HTML, CSS and vanilla JavaScript.

The contact form currently performs frontend validation and displays a demo success state. It is not yet wired to the backend endpoint.

## Backend

The backend uses Java 21, Spring Boot 3.5.6, Spring Web, Spring Validation, Spring Data JPA, PostgreSQL and Maven.

The current API endpoint is:

`POST /api/contact`

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

`api.yaml` is the reference for the current public API contract. The current contract contains the contact endpoint only.
