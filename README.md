# Event Ticket Booking API

A REST API for creating events and booking seats. Organisers create events and see sales reports for their own
events. Any logged-in user can book seats. Authentication uses JWT, data lives in PostgreSQL, and the whole
stack runs in Docker.

## Contents

- [Tech stack](#tech-stack)
- [Quick start (Docker)](#quick-start-docker)
- [Run locally without Docker](#run-locally-without-docker)
- [Configuration](#configuration)
- [API overview](#api-overview)
- [Business rules](#business-rules)
- [Error format](#error-format)
- [Security model](#security-model)
- [Concurrency](#concurrency)
- [Logging](#logging)
- [Database](#database)
- [Testing and coverage](#testing-and-coverage)
- [Project structure](#project-structure)
- [Known limitations](#known-limitations)

## Tech stack

| Area | Choice |
|---|---|
| Language / build | Java 21, Gradle (Kotlin DSL) |
| Framework | Spring Boot 4.1.1 (Web MVC, Data JPA, Validation, Actuator) |
| Security | Spring Security, stateless JWT (jjwt 0.12.6), BCrypt passwords |
| Database | PostgreSQL 16, schema managed by Liquibase |
| API docs | springdoc OpenAPI (Swagger UI) |
| Tests | JUnit 5, Mockito, AssertJ, MockMvc, Testcontainers (PostgreSQL), JaCoCo |

## Quick start (Docker)

You need Docker Desktop (or Docker Engine with the Compose plugin).

1. **Create a `.env` file** in the project root (never commit it):

   ```env
   SERVER_PORT=8080
   HOST_PORT=8080
   DB_NAME=event_ticket_booking
   DB_USERNAME=postgres
   DB_PASSWORD=choose-a-strong-password
   JWT_SECRET=paste-a-generated-secret-here
   JWT_EXPIRATION_MINUTES=60
   APP_TIMEZONE=Asia/Kolkata
   ```

   Generate the secret (it must be at least 32 bytes):

   ```bash
   openssl rand -base64 48
   ```

2. **Start everything:**

   ```bash
   docker compose up --build -d
   docker compose ps          # wait until the app shows "healthy"
   docker compose logs -f app
   ```

3. **Check it works:**

   - Health: <http://localhost:8080/actuator/health>
   - Swagger UI: <http://localhost:8080/swagger-ui/index.html>

Liquibase creates all tables on the first start.

### Useful Docker commands

| Task | Command |
|---|---|
| Stop, keep the data | `docker compose down` |
| Stop and delete the database | `docker compose down -v` |
| Rebuild after code changes | `docker compose up --build -d` |
| Follow app logs | `docker compose logs -f app` |
| SQL shell | `docker compose exec db psql -U postgres -d <DB_NAME>` |

> **Changing `DB_NAME`, `DB_USERNAME` or `DB_PASSWORD` after the first start has no effect on the existing
> volume.** PostgreSQL only reads them when the volume is first created. Run `docker compose down -v` to
> start over (this deletes the data).

### Connecting a database client (DBeaver, psql)

Publish the database port on localhost only, in `docker-compose.yml` under the `db` service:

```yaml
    ports:
      - "127.0.0.1:${DB_HOST_PORT:-5433}:5432"
```

Then connect to host `localhost`, port `5433`, with the database name, username and password from `.env`.
Port 5433 avoids clashing with a PostgreSQL installed on your machine. Never publish this port on a server.

## Run locally without Docker

Requirements: JDK 21 and a PostgreSQL database.

```bash
# start only the database from the compose file (published on localhost:5433, see above)
docker compose up -d db

# Linux / macOS / Git Bash
export JWT_SECRET="any-string-of-at-least-32-characters-long"
export DB_URL="jdbc:postgresql://localhost:5433/event_ticket_booking"
export DB_USERNAME=postgres
export DB_PASSWORD=choose-a-strong-password
./gradlew bootRun
```

PowerShell: `$env:JWT_SECRET="..."; $env:DB_URL="..."; ./gradlew bootRun`.

## Configuration

All settings come from environment variables. `JWT_SECRET` has no default on purpose: the application refuses
to start without it.

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8080` | Port the application listens on |
| `DB_URL` | `jdbc:postgresql://localhost:5432/postgres` | JDBC URL |
| `DB_USERNAME` | `postgres` | Database user |
| `DB_PASSWORD` | `postgres` | Database password |
| `JWT_SECRET` | none (required) | HMAC signing key, at least 32 bytes |
| `JWT_EXPIRATION_MINUTES` | `60` | Token lifetime |
| `LOG_FILE` | `logs/application.log` | JSON log file location |

Docker Compose adds `HOST_PORT` (port on your machine), `DB_NAME` (database created on first start) and
`APP_TIMEZONE` (see [Business rules](#business-rules)).

## API overview

Interactive documentation: **Swagger UI** at `/swagger-ui/index.html`. After logging in, send the token as
`Authorization: Bearer <access_token>`.

| Method | Path | Who can call it | Purpose |
|---|---|---|---|
| POST | `/auth/register` | public | Create an account |
| POST | `/auth/login` | public | Get a JWT |
| POST | `/api/events` | ORGANISER | Create an event (the caller becomes its owner) |
| GET | `/api/events/{id}` | any logged-in user | View an event |
| GET | `/api/events/{id}/seats` | any logged-in user | Booked and available seat numbers |
| GET | `/api/events/{id}/bookings` | ORGANISER, owner of the event | Paged list of the event's bookings |
| GET | `/api/events/{id}/summary` | ORGANISER, owner of the event | Sales summary |
| POST | `/api/bookings` | any logged-in user | Book seats |
| GET | `/api/bookings/{id}` | the booking's owner, or the event's organiser | View a booking |
| GET | `/actuator/health` | public | Health check |

### Example flow

```bash
# 1. register an organiser and a customer
curl -X POST localhost:8080/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"org@example.com","password":"password123","role":"ORGANISER"}'
curl -X POST localhost:8080/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"fan@example.com","password":"password123"}'

# 2. log in (copy access_token from the response)
curl -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"org@example.com","password":"password123"}'

# 3. organiser creates an event
curl -X POST localhost:8080/api/events -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ORG_TOKEN" \
  -d '{"name":"Rock Night","category":"MUSIC","base_price":100.00,"total_seats":200,
       "event_date":"2027-03-15","venue":"Main Hall"}'

# 4. customer books seats
curl -X POST localhost:8080/api/bookings -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $FAN_TOKEN" \
  -d '{"event_id":1,"customer_name":"John Doe","customer_email":"fan@example.com","seat_numbers":[1,2]}'

# 5. organiser reads the report
curl -H "Authorization: Bearer $ORG_TOKEN" "localhost:8080/api/events/1/summary"
curl -H "Authorization: Bearer $ORG_TOKEN" "localhost:8080/api/events/1/bookings?page=0&size=20"
```

On Windows PowerShell, `curl` is an alias with different quoting rules. Use `curl.exe`, `Invoke-RestMethod`, or
simply Swagger UI.

### Request fields

**Register**: `email`, `password` (8 to 72 characters), optional `role` (`ORGANISER` or `CUSTOMER`, default
`CUSTOMER`).

**Create event**: `name` (3 to 100), `category` (`MUSIC`, `SPORTS`, `TECH`, `THEATRE`), `base_price` (greater
than 0, up to 2 decimals), `total_seats` (10 to 10000), `event_date` (`yyyy-mm-dd`, in the future), `venue`
(3 to 100). The same name on the same date is a duplicate.

**Book**: `event_id`, `customer_name` (3 to 50 letters and spaces), `customer_email`, `seat_numbers` (1 to 6
distinct positive integers).

### Paged bookings list

`GET /api/events/{id}/bookings?status=&page=0&size=20`

- `status` is optional: exactly `CONFIRMED` or `REJECTED`.
- `page` is 0-based (default 0). `size` is 1 to 100 (default 20).
- Order is fixed: newest first (`booked_on desc, id desc`), so pages never overlap or skip.
- Response:

  ```json
  { "content": [ ... ], "page": 0, "size": 20, "total_elements": 45, "total_pages": 3 }
  ```

The reasoning is recorded in [`docs/adr/0001-paginate-event-bookings-list.md`](docs/adr/0001-paginate-event-bookings-list.md).

## Business rules

**A booking attempt always returns `201`**, with status `CONFIRMED` or `REJECTED`. Rejected attempts are stored
too, with a `rejection_reason`. Rules are checked in this order and the first failure decides:

1. **Event already over:** the event date is before today. Reason: `Event is already over`.
2. **Seats taken:** any requested seat is already confirmed for this event. Reason: `Seats already booked: 2, 3`.
3. **Per-customer limit:** one customer email (case-insensitive) may hold at most **6 confirmed seats per event**.

**Pricing** (confirmed bookings only, rounded half-up to 2 decimals):

| Rule | Condition | Effect |
|---|---|---|
| Early bird | event is 30 or more days away | price per seat x 0.90 |
| Group | 4 or more seats in the booking | price per seat x 0.95 |

Both discounts stack: 100.00 becomes 85.50 per seat for a 4-seat booking made 45 days ahead.

**Demand band** (event summary, from the rounded occupancy percentage): below 40 is `LOW`, 40 to 75 inclusive
is `MEDIUM`, above 75 is `HIGH`.

**Dates and time zones:** "today" is the server's local date. In Docker it follows `APP_TIMEZONE`, so set it
to where your events happen, otherwise the "event is over" and early-bird rules flip at the wrong hour.

## Error format

Every error uses the same JSON shape:

```json
{ "error": "VALIDATION_FAILED", "message": "Request validation failed",
  "details": [ { "field": "seat_numbers", "message": "must contain 1 to 6 seat numbers" } ] }
```

`details` appears only for validation errors, with field names in snake_case, sorted alphabetically.

| Status | `error` code | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Invalid fields, bad `status`, `page` or `size` |
| 400 | `INVALID_REQUEST` | Malformed JSON, missing body, bad path id |
| 401 | `UNAUTHORIZED` | Missing, invalid or expired token |
| 401 | `INVALID_CREDENTIALS` | Wrong email or password (the message never says which) |
| 403 | `FORBIDDEN` | Wrong role, or not the owner of the event |
| 404 | `NOT_FOUND` | Unknown event, or a booking the caller may not see |
| 409 | `DUPLICATE_EVENT` | Same name and date already exists |
| 409 | `USER_ALREADY_EXISTS` | Email already registered |
| 500 | `INTERNAL_ERROR` | Unexpected error (details are never leaked) |

Framework errors such as 405 and 415 use the same shape with the HTTP status name as the code.

## Security model

- **Stateless JWT:** HS256, claims `sub` (user id), `email`, `role`. The role is read from the token, with no
  database lookup per request, so a role change takes effect when the token expires. There are no refresh
  tokens.
- **Passwords:** stored as BCrypt hashes. Emails are trimmed and lower-cased.
- **Role rules** are enforced in `SecurityConfig` by URL.
- **Resource-level rules** are enforced in the services, because a role alone is not enough:
  - An organiser can read the bookings and summary of **their own events only** (403 otherwise). An event with
    no owner is readable by nobody.
  - A booking is visible to the user who made it and to the organiser of its event. Everyone else gets 404,
    not 403, so booking ids cannot be probed.
- **Login timing:** an unknown email costs the same time as a wrong password, so accounts cannot be
  enumerated.

## Concurrency

Booking is safe under simultaneous requests, in two layers:

1. **Row lock:** `createBooking` starts a transaction and locks the event row (`SELECT ... FOR UPDATE`).
   Concurrent bookings for the same event queue up, so the seat check, the 6-seat check and the insert are
   never interleaved. Different events do not block each other.
2. **Database constraint:** a partial unique index on `booking_seats (event_id, seat_number) WHERE status =
   'CONFIRMED'` makes a double booking impossible even if the lock were bypassed.

Event and user creation check first and rely on unique constraints plus `saveAndFlush`, so a race becomes a
clean 409.

## Logging

One structured JSON log line per request (INFO for success, WARN for 4xx, ERROR with stack trace for 5xx),
written by `RequestIdFilter`. Each line carries these MDC fields where they apply: `requestId`, `method`,
`path`, `userId`, `eventId`, `bookingId`, `bookingStatus`, `status`, `durationMs`.

- The `X-Request-Id` response header returns the request id. A safe client-supplied id is reused.
- Passwords and tokens are never logged.
- Logs go to the console and to `logs/application.log` (`LOG_FILE`).

## Database

Schema changes live in `src/main/resources/db/changelog/` and run automatically on startup. Hibernate only
validates (`ddl-auto: validate`) and never alters the schema.

| File | Change |
|---|---|
| `001-create-event-table.yaml` | `events`, unique on (name, event_date) |
| `002-create-bookings.yaml` | `bookings` |
| `003-create-booking-seats.yaml` | `booking_seats` and the partial unique index |
| `004-create-users.yaml` | `users` (email, BCrypt password, role) |
| `005-bookings-add-user-id.yaml` | `bookings.user_id` (nullable: older bookings have no owner) |
| `006-events-add-organiser-id.yaml` | `events.organiser_id` (nullable: older events have no owner) |

Never edit a changeset that has already run. Add a new numbered file and include it in
`db.changelog-master.yaml`.

## Testing and coverage

Docker must be running: integration tests start a real PostgreSQL with Testcontainers.

```bash
./gradlew test
```

JaCoCo runs after the tests. The HTML report is at `build/reports/jacoco/test/html/index.html`. The build
**fails if instruction or line coverage drops below 80%** (only the class holding `main()` is excluded).

| Kind | What it covers |
|---|---|
| Unit tests | Pricing, validators, services, JWT, filters, exception handler |
| Integration tests | Full HTTP flows against real PostgreSQL: auth, events, bookings, role and ownership rules, logging MDC, repository queries |
| Concurrency tests | Parallel bookings: one winner per seat, the 6-seat limit under a race, lock waiting, the database index as a safety net |

## Project structure

```
src/main/java/com/ionixx/Event_Ticket_Booking/
  controller/              REST endpoints (Auth, Event, Booking)
  service/                 Business logic (booking, pricing, reports, auth)
  repository/              Spring Data repositories
  entity/                  JPA entities (Event, Booking, BookingSeat, User)
  dto/                     Request and response records
  security/                JWT service and filter, security config, 401/403 handlers
  filter/                  RequestIdFilter (request id, MDC, single log line)
  globalExceptionHandler/  Exceptions and the central error mapping
  validators/              Booking and date validation
  util/                    ID and paging parameter parsing
  enums/                   Role, Category, DemandBand
src/main/resources/
  application.yaml
  db/changelog/            Liquibase changesets
src/test/                  Unit, integration and concurrency tests
docs/adr/                  Architecture decision records
```
