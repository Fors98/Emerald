# Emerald API Tests

## Environment

- Base URL: `http://localhost:8080/api`
- Endpoint: `POST /contact`
- Database: PostgreSQL `emerald_db`
- API contract: `api.yaml`

## Test Results

### 1. Valid contact submission

**Expected:** `201 Created`
**Actual:** `201 Created`
**Result:** PASS

Response contained `id`, `receivedAt`, and `status: "received"`.

The created contact was verified directly in PostgreSQL.

### 2. Invalid field values

**Request:** Empty name, invalid email, and message shorter than 10 characters.

**Expected:** `400 Bad Request`
**Actual:** `400 Bad Request`
**Result:** PASS

Validation errors were returned for `name`, `email`, and `message`.

### 3. Missing required fields

**Request:** `{}`

**Expected:** `400 Bad Request`
**Actual:** `400 Bad Request`
**Result:** PASS

Validation errors were returned for `name`, `email`, and `message`.

## Contract Coverage

| Case | Expected | Tested | Result |
|---|---:|---:|---|
| Valid contact | 201 | Yes | PASS |
| Invalid name | 400 | Yes | PASS |
| Invalid email | 400 | Yes | PASS |
| Message shorter than 10 characters | 400 | Yes | PASS |
| Missing required fields | 400 | Yes | PASS |
| Rate limiting | 429 | No | Not tested |
| Server error | 500 | No | Not tested |

## Database Verification

The successful contact submission was verified in PostgreSQL. The stored record contained the submitted name, email, company, project type, message, generated UUID, and received timestamp.

## Conclusion

The tested `POST /api/contact` validation and persistence flow is working against the current `api.yaml` contract.

The `429` and `500` responses are documented by the API contract but were not artificially triggered during this test session.
