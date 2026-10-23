package com.ionixx.Event_Ticket_Booking.integration;

import com.ionixx.Event_Ticket_Booking.entity.Event;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The booking flow end to end on a real PostgreSQL: the whole journey through the HTTP API, what
 * actually lands in the tables, and races between concurrent requests that only a real database
 * (row locks, unique constraints, transactions) can settle.
 */
class BookingFlowIT extends AbstractApiIT {

    private static final String MAX_REASON = "Customer already holds the maximum seats for this event";

    // =====================================================================
    // the journey
    // =====================================================================

    @Test
    void completeBookingJourney() throws Exception {
        // 1. publish an event through the API: 71 days away, 20 seats, base 1500
        String created = postJson("/api/events", eventBody(
                "name", "Chennai Jazz Night", "total_seats", 20, "base_price", 1500,
                "event_date", inDays(71).toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long eventId = ((Number) JsonPath.read(created, "$.id")).longValue();
        String eventUrl = "/api/events/" + eventId;

        // 2. nothing is booked yet
        getJson(eventUrl + "/seats")
                .andExpect(jsonPath("$.booked_seats", hasSize(0)))
                .andExpect(jsonPath("$.available_seats", hasSize(20)));

        // 3. Priya books 12-15: early-bird and group discount -> 1282.50 each, 5130.00 total
        ResultActions priya1 = book(eventId, "Priya Raman", "priya@example.com", 12, 13, 14, 15);
        priya1.andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.price_per_seat").value(1282.50))
                .andExpect(jsonPath("$.total_amount").value(5130.00));
        long priya1Id = idOf(priya1);

        // 4. Ravi asks for 14 (taken) and 16: REJECTED, and 16 stays free
        book(eventId, "Ravi Kumar", "ravi@example.com", 14, 16)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejection_reason").value("Seats already booked: 14"));

        // 5. Ravi books 16-17: early-bird only -> 1350.00 each, 2700.00
        book(eventId, "Ravi Kumar", "ravi@example.com", 16, 17)
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.price_per_seat").value(1350.00))
                .andExpect(jsonPath("$.total_amount").value(2700.00));

        // 6. Priya holds 4 and asks for 3 more: over the limit of 6
        book(eventId, "Priya Raman", "priya@example.com", 1, 2, 3)
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejection_reason").value(MAX_REASON));

        // 7. ...but 2 more still fits (4 + 2 = 6); the rejected request did not count
        book(eventId, "Priya Raman", "priya@example.com", 1, 2)
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.total_amount").value(2700.00));

        // 8. the seat map
        getJson(eventUrl + "/seats")
                .andExpect(jsonPath("$.booked_seats", contains(1, 2, 12, 13, 14, 15, 16, 17)))
                .andExpect(jsonPath("$.available_seats", contains(3, 4, 5, 6, 7, 8, 9, 10, 11, 18, 19, 20)));

        // 9. the summary: 8 seats, 3 confirmed, 2 rejected, 5130 + 2700 + 2700
        getJson(eventUrl + "/summary")
                .andExpect(jsonPath("$.seats_sold").value(8))
                .andExpect(jsonPath("$.seats_available").value(12))
                .andExpect(jsonPath("$.confirmed_bookings").value(3))
                .andExpect(jsonPath("$.rejected_bookings").value(2))
                .andExpect(jsonPath("$.total_revenue").value(10530.00))
                .andExpect(jsonPath("$.occupancy_pct").value(40.0))
                .andExpect(jsonPath("$.demand_band").value("MEDIUM"));

        // 10. the listing: newest first, and the status filter
        getJson(eventUrl + "/bookings")
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[*].status",
                        contains("CONFIRMED", "REJECTED", "CONFIRMED", "REJECTED", "CONFIRMED")));
        getJson(eventUrl + "/bookings?status=CONFIRMED").andExpect(jsonPath("$", hasSize(3)));
        getJson(eventUrl + "/bookings?status=REJECTED").andExpect(jsonPath("$", hasSize(2)));

        // 11. the first booking reads back exactly as it was created
        getJson("/api/bookings/" + priya1Id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seat_numbers", contains(12, 13, 14, 15)))
                .andExpect(jsonPath("$.total_amount").value(5130.00));
    }

    // =====================================================================
    // schema and constraints (what only a real PostgreSQL can tell you)
    // =====================================================================

    @Test
    void schemaIsBuiltByLiquibase_onPostgres() {
        String version = jdbc.queryForObject("select version()", String.class);
        assertTrue(version != null && version.contains("PostgreSQL"), version);

        Integer changeSets = jdbc.queryForObject("select count(*) from databasechangelog", Integer.class);
        assertTrue(changeSets != null && changeSets > 0, "Liquibase did not run");

        for (String table : List.of("events", "bookings", "booking_seats")) {
            Integer found = jdbc.queryForObject(
                    "select count(*) from information_schema.tables "
                            + "where table_schema = current_schema() and table_name = ?",
                    Integer.class, table);
            assertEquals(1, found, "missing table " + table);
        }
    }

    @Test
    void database_itself_rejectsDuplicateNameAndDate() {
        saveEvent("Dup Show", inDays(10), 50, "1000");

        // goes straight to the repository, so only the DB constraint can stop it
        assertThrows(DataIntegrityViolationException.class,
                () -> saveEvent("Dup Show", inDays(10), 50, "1000"));
    }

    @Test
    void confirmedAndRejectedBookings_areStoredCorrectly() throws Exception {
        Event event = saveEvent("Persist Show", inDays(71), 200, "1500");
        long confirmedId = idOf(book(event.getId(), "Priya Raman", "a@example.com", 12, 13, 14, 15));
        long rejectedId = idOf(book(event.getId(), "Ravi Kumar", "b@example.com", 14, 20));

        // money keeps two decimals
        assertEquals(0, new BigDecimal("1282.50").compareTo(
                jdbc.queryForObject("select price_per_seat from bookings where id = ?", BigDecimal.class, confirmedId)));
        assertEquals(0, new BigDecimal("5130.00").compareTo(
                jdbc.queryForObject("select total_amount from bookings where id = ?", BigDecimal.class, confirmedId)));
        assertEquals(List.of(12, 13, 14, 15), jdbc.queryForList(
                "select seat_number from booking_seats where booking_id = ? order by seat_number",
                Integer.class, confirmedId));
        assertEquals(List.of("CONFIRMED"), jdbc.queryForList(
                "select distinct status from booking_seats where booking_id = ?", String.class, confirmedId));

        // a rejected booking is saved: reason set, amounts null, its seat rows marked REJECTED
        Map<String, Object> row = jdbc.queryForMap(
                "select status, rejection_reason, price_per_seat, total_amount, seats from bookings where id = ?",
                rejectedId);
        assertEquals("REJECTED", row.get("status"));
        assertEquals("Seats already booked: 14", row.get("rejection_reason"));
        assertNull(row.get("price_per_seat"));
        assertNull(row.get("total_amount"));
        assertEquals(2, ((Number) row.get("seats")).intValue());
        assertEquals(List.of("REJECTED"), jdbc.queryForList(
                "select distinct status from booking_seats where booking_id = ?", String.class, rejectedId));
    }

    @Test
    void largestEvent_10000Seats_seatMapAndRangeCheck() throws Exception {
        Event event = saveEvent("Stadium", inDays(10), 10000, "500");
        book(event.getId(), "Priya Raman", "a@example.com", 1, 10000)
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        getJson("/api/events/" + event.getId() + "/seats")
                .andExpect(jsonPath("$.total_seats").value(10000))
                .andExpect(jsonPath("$.booked_seats", contains(1, 10000)))
                .andExpect(jsonPath("$.available_seats", hasSize(9998)));

        book(event.getId(), "Priya Raman", "b@example.com", 10001)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].message").value("seat 10001 does not exist for this event"));
    }

    // =====================================================================
    // concurrency: real row locks and unique constraints
    // =====================================================================

    @Test
    void sameEventCreatedConcurrently_exactlyOneSucceeds_theRestGet409() throws Exception {
        String body = eventBody();
        List<Callable<Resp>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tasks.add(() -> send("/api/events", body));
        }

        List<Resp> results = runConcurrently(tasks);

        assertEquals(1, count(results, 201), "exactly one create may win");
        assertEquals(5, count(results, 409));
        results.stream().filter(r -> r.status() == 409)
                .forEach(r -> assertEquals("DUPLICATE_EVENT", r.read("$.error")));
        assertEquals(1, eventRepository.count());
    }

    @Test
    void sameCustomerRacingForDifferentSeats_neverHoldsMoreThanSix() throws Exception {
        Event event = saveEvent("Race Show", inDays(10), 100, "1000");
        String email = "racer@example.com";
        List<Callable<Resp>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            final int first = i * 3 + 1;
            tasks.add(() -> send("/api/bookings", bookingBody(
                    "event_id", event.getId(), "customer_email", email,
                    "seat_numbers", List.of(first, first + 1, first + 2))));
        }

        List<Resp> results = runConcurrently(tasks);

        assertEquals(2, countStatus(results, "CONFIRMED"), "3 + 3 seats fit, the other two must not");
        assertEquals(2, countStatus(results, "REJECTED"));
        results.stream().filter(r -> "REJECTED".equals(r.read("$.status")))
                .forEach(r -> assertEquals(MAX_REASON, r.read("$.rejection_reason")));
        assertEquals(6L, bookingRepository.countConfirmedSeatsForCustomer(event.getId(), email));
    }

    @Test
    void overlappingSeatRequests_neverDoubleBookASeat() throws Exception {
        Event event = saveEvent("Overlap Show", inDays(10), 100, "1000");
        List<Callable<Resp>> tasks = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            final int seat = i;
            final String email = "user" + i + "@example.com";
            tasks.add(() -> send("/api/bookings", bookingBody(
                    "event_id", event.getId(), "customer_email", email,
                    "seat_numbers", List.of(seat, seat + 1))));      // 1-2, 2-3, 3-4, ... 6-7
        }

        List<Resp> results = runConcurrently(tasks);

        results.forEach(r -> assertEquals(201, r.status()));
        int confirmed = countStatus(results, "CONFIRMED");
        assertTrue(confirmed >= 1 && confirmed <= 3, "at most 3 of these pairs can be disjoint, got " + confirmed);

        Integer heldSeats = jdbc.queryForObject(
                "select count(*) from booking_seats where event_id = ? and status = 'CONFIRMED'",
                Integer.class, event.getId());
        Integer distinctSeats = jdbc.queryForObject(
                "select count(distinct seat_number) from booking_seats where event_id = ? and status = 'CONFIRMED'",
                Integer.class, event.getId());
        assertEquals(distinctSeats, heldSeats, "a seat is held by two confirmed bookings");
        assertEquals(confirmed * 2, heldSeats);
    }

    // ---------- helpers ----------

    private ResultActions book(long eventId, String name, String email, Integer... seats) throws Exception {
        return postJson("/api/bookings", bookingBody(
                "event_id", eventId, "customer_name", name, "customer_email", email,
                "seat_numbers", Arrays.asList(seats)));
    }

    private static long idOf(ResultActions result) throws Exception {
        String json = result.andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private record Resp(int status, String body) {
        <T> T read(String path) {
            return JsonPath.read(body, path);
        }
    }

    private Resp send(String url, String body) throws Exception {
        MockHttpServletResponse res = postJson(url, body).andReturn().getResponse();
        return new Resp(res.getStatus(), res.getContentAsString());
    }

    private static long count(List<Resp> results, int status) {
        return results.stream().filter(r -> r.status() == status).count();
    }

    private static int countStatus(List<Resp> results, String bookingStatus) {
        return (int) results.stream().filter(r -> bookingStatus.equals(r.read("$.status"))).count();
    }

    /** Starts all tasks at the same instant and returns their results (keep it under the connection pool size). */
    private <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "threads did not start");
            go.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
