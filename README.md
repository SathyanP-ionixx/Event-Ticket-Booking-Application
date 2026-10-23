# Event Ticket Booking Service

A small Spring Boot REST service for publishing events, booking numbered seats, and reporting how
well each event is selling. Pricing rules (early-bird, group discount) are applied automatically.

**Stack:** Java 21, Spring Boot, Spring Data JPA (Hibernate), Liquibase, PostgreSQL, Gradle.

## Run it

1. Start PostgreSQL (or use one you already have):
   ```bash
   docker run --name event-pg -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres:16
   ```
2. The app expects `jdbc:postgresql://localhost:5432/postgres` with user/password `postgres` / `postgres`
   (see `src/main/resources/application.yaml`). **No SQL to run by hand**: Liquibase creates the tables on
   start-up (`db/changelog/db.changelog-master.yaml`) and Hibernate only validates them.
3. Start the service:
   ```bash
   ./gradlew bootRun          # Windows: gradlew.bat bootRun
   ```
4. It listens on `http://localhost:8080`. Health check: `GET /actuator/health`.

## API

| # | Method and path | Success | Errors |
|---|---|---|---|
| 1 | `POST /api/events` | 201 + event | 400 validation / invalid body, 409 duplicate |
| 2 | `GET /api/events/{id}` | 200 + event | 400 bad id, 404 |
| 3 | `POST /api/bookings` | 201 + booking (`CONFIRMED` **or** `REJECTED`) | 400 validation / invalid body, 404 event |
| 4 | `GET /api/bookings/{id}` | 200 + booking | 400 bad id, 404 |
| 5 | `GET /api/events/{id}/bookings?status=` | 200 + array, newest first (`[]` if none) | 400 bad id / bad status, 404 |
| 6 | `GET /api/events/{id}/seats` | 200 + seat map | 400 bad id, 404 |
| 7 | `GET /api/events/{id}/summary` | 200 + sales summary | 400 bad id, 404 |

Request and response bodies use `snake_case` JSON. Examples:

```bash
curl -X POST localhost:8080/api/events -H 'Content-Type: application/json' -d '{
  "name": "Chennai Jazz Night", "venue": "Music Academy, Chennai", "category": "MUSIC",
  "event_date": "2026-12-20", "total_seats": 200, "base_price": 1500 }'

curl -X POST localhost:8080/api/bookings -H 'Content-Type: application/json' -d '{
  "event_id": 1, "customer_name": "Priya Raman", "customer_email": "priya@example.com",
  "seat_numbers": [12, 13, 14, 15] }'

curl 'localhost:8080/api/events/1/bookings?status=CONFIRMED'
curl  localhost:8080/api/events/1/seats
curl  localhost:8080/api/events/1/summary
```

## Booking rules

A booking is validated first (400 on failure). Then the first rule that fails decides a `REJECTED`
booking, which is still saved and returned with `201`:

1. **Date:** the event date must be today or later, else `Event is already over`.
2. **Availability:** no requested seat may belong to a `CONFIRMED` booking, else `Seats already booked: 12, 14`.
3. **Per customer:** the same email (case-insensitive) holds at most 6 confirmed seats per event, else
   `Customer already holds the maximum seats for this event`.

Only `CONFIRMED` bookings occupy seats. Pricing for a confirmed booking: 10% off if the event is 30 or
more days away, then a further 5% off for 4 or more seats, rounded to 2 decimals
(1500, 71 days, 4 seats -> 1282.50 per seat, 5130.00 total).

Concurrent bookings for one event are serialized with a pessimistic row lock on the event, so two
customers can never both confirm the same seat.

**Summary:** `occupancy_pct` = seats sold / total seats x 100, rounded to 1 decimal. `demand_band` is
`LOW` below 40, `MEDIUM` from 40 to 75 inclusive, `HIGH` above 75, taken from that rounded value.

## Errors

Every error is JSON, and no stack trace ever reaches the client.

| Status | `error` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Invalid fields. `details` lists **every** failing field as `{field, message}`. |
| 400 | `INVALID_REQUEST` | Malformed or empty JSON, wrong JSON types, or an id that is not a positive integer. |
| 404 | `NOT_FOUND` | Event or booking does not exist. |
| 409 | `DUPLICATE_EVENT` | An event with the same name and date already exists. |
| 500 | `INTERNAL_ERROR` | Anything unexpected. |

## Logging

`RequestIdFilter` writes exactly one JSON log line per request to `logs/application.log`: INFO for
success, WARN for 4xx, ERROR with stack trace for 5xx. It carries `requestId` (also returned in the
`X-Request-Id` header; a safe client-supplied id is reused), `method`, `path`, and `eventId`,
`bookingId` and `bookingStatus` where they apply.

## Tests

```bash
./gradlew test                       # everything
./gradlew test --tests '*Test'       # unit tests only: fast, no Docker needed
./gradlew test --tests '*IT'         # integration tests only
```

| Layer | Classes | Notes |
|---|---|---|
| Unit | `PriceCalculatorTest`, `BookingValidatorTest`, `FutureIsoDateValidatorTest`, `EventRequestTest` | Pure JUnit 5. |
| Service | `BookingServiceTest`, `EventServiceTest`, `EventReportServiceTest` | Mockito, no database. |
| Integration | `EventApiIT`, `BookingApiIT`, `EventReportApiIT`, `BookingConcurrencyIT`, `BookingFlowIT` | Full application on **real PostgreSQL via Testcontainers**. |

**Integration tests need Docker running.** They start one `postgres:16-alpine` container for the whole
test run (the first run pulls the image) and remove it afterwards. If Docker is not available they are
reported as *skipped*, not passed, so check the test report. They deliberately keep Liquibase on and
`ddl-auto: validate`, so the tests also fail if the changelog no longer matches the entities.

`BookingFlowIT` covers the full journey (create event, book, reject, seat map, summary, listing), checks
what is stored in the tables, and races concurrent requests against real row locks and unique
constraints: duplicate event creation, one customer chasing more than 6 seats, and overlapping seat
requests.

## Project layout

```
src/main/java/com/ionixx/Event_Ticket_Booking
  controller/               EventController, BookingController
  service/                  EventService, BookingService, EventReportService, PriceCalculator
  repository/               Spring Data repositories (+ locking and aggregate queries)
  entity/  dto/  enums/     Event, Booking, BookingSeat / request and response records / Category, DemandBand
  validators/               BookingValidator, @FutureIsoDate
  globalExceptionHandler/   one place that maps every failure to a JSON error body
  filter/                   RequestIdFilter (request id + single log line per request)
src/test/java/...           unit, service and integration tests
```
