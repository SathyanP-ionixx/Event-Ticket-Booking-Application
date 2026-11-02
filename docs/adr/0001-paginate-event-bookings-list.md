# ADR 0001: Paginate the event bookings list

**Status:** Accepted, 2026-10-29

## Context
`GET /api/events/{id}/bookings` returned every booking of an event as one JSON array. A popular event makes
the response and the memory use unbounded. The query also fetch-joined `bookingSeats`; a collection fetch
join cannot be paged in SQL (Hibernate would load everything and page in memory).

## Decision
- **Offset pagination:** `page` (0-based, default 0) and `size` (default 20, max 100). Invalid values give
  `400 VALIDATION_FAILED` with the field named, like `status` does.
- **Envelope:** `{content, page, size, total_elements, total_pages}` in our own `PagedResponse` record, not
  Spring's `Page`, whose JSON shape is not a stable contract.
- **Fixed order:** `booked_on desc, id desc`. `id` is a unique tie-breaker, so pages never overlap or skip.
  There is no client-supplied sort.
- **Seats without a fetch join:** the page query selects plain bookings and `@BatchSize(100)` on
  `Booking.bookingSeats` loads the seats of the whole page in one query (page + count + seats, never one
  query per booking). 100 matches the maximum page size.

## Consequences
- (+) Response size, memory and query count stay bounded however many bookings exist.
- (-) **Breaking change:** the body is now an object, not an array. Clients must read `content`.
- (-) Offset paging is not stable while bookings arrive: with newest-first order a new booking shifts rows
  to the next page, so a client walking pages can see one row twice. Deep pages cost more (`OFFSET`), and
  each request runs a count query.
- (-) The `@BatchSize` value is tied to the maximum page size; raise both together.

## Alternatives considered
- **Keyset (cursor) pagination:** stable under inserts and constant-cost on deep pages, but no random page
  access or total count, and a more complex API. Organisers browse a few pages, so it is not worth it yet;
  revisit if events reach tens of thousands of bookings.
- **Keep the array and add an `X-Total-Count` header:** not breaking, but non-standard, and clients still
  could not tell page size or page count from the body.
- **Fetch join plus paging:** rejected, see Context.
