package com.ionixx.Event_Ticket_Booking.integration;

import com.ionixx.Event_Ticket_Booking.entity.Event;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API 3 (POST /api/bookings) and API 4 (GET /api/bookings/{id}). */
class BookingApiIT extends AbstractApiIT {

    private static final String MAX_REASON = "Customer already holds the maximum seats for this event";

    // ---------- helpers ----------

    private ResultActions book(Event event, String email, Integer... seats) throws Exception {
        return postJson("/api/bookings",
                bookingBody("event_id", event.getId(), "customer_email", email, "seat_numbers", Arrays.asList(seats)));
    }

    private void assertConfirmed(ResultActions r) throws Exception {
        r.andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.rejection_reason").value(nullValue()));
    }

    private void assertRejected(ResultActions r, String reason) throws Exception {
        r.andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejection_reason").value(reason))
                .andExpect(jsonPath("$.price_per_seat").value(nullValue()))
                .andExpect(jsonPath("$.total_amount").value(nullValue()));
    }

    // =====================================================================
    // API 3 - confirmed bookings and pricing
    // =====================================================================

    @Test
    void workedExample_71Days_4Seats_base1500_is1282_50_and_5130_00() throws Exception {
        Event event = saveEvent("Chennai Jazz Night", inDays(71), 200, "1500");

        postJson("/api/bookings", bookingBody("event_id", event.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.event_id").value(event.getId()))
                .andExpect(jsonPath("$.customer_name").value("Priya Raman"))
                .andExpect(jsonPath("$.customer_email").value("priya@example.com"))
                .andExpect(jsonPath("$.seat_numbers", contains(12, 13, 14, 15)))
                .andExpect(jsonPath("$.seats").value(4))
                .andExpect(jsonPath("$.price_per_seat").value(1282.50))
                .andExpect(jsonPath("$.total_amount").value(5130.00))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.rejection_reason").value(nullValue()))
                .andExpect(jsonPath("$.booked_on").value(inDays(0).toString()));
    }

    @Test
    void checklist_2Seats_10DaysAway_base1000_noDiscount() throws Exception {
        Event event = saveEvent("Small Gig", inDays(10), 200, "1000");

        book(event, "priya@example.com", 1, 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.price_per_seat").value(1000.00))
                .andExpect(jsonPath("$.total_amount").value(2000.00));
    }

    @Test
    void earlyBird_startsAtExactly30Days() throws Exception {
        Event day29 = saveEvent("Day 29", inDays(29), 200, "1000");
        Event day30 = saveEvent("Day 30", inDays(30), 200, "1000");

        book(day29, "a@example.com", 1).andExpect(jsonPath("$.price_per_seat").value(1000.00));
        book(day30, "a@example.com", 1).andExpect(jsonPath("$.price_per_seat").value(900.00));
    }

    @Test
    void groupDiscount_startsAtExactly4Seats() throws Exception {
        Event event = saveEvent("Group Show", inDays(10), 200, "1000");

        book(event, "a@example.com", 1, 2, 3).andExpect(jsonPath("$.price_per_seat").value(1000.00));
        book(event, "b@example.com", 11, 12, 13, 14)
                .andExpect(jsonPath("$.price_per_seat").value(950.00))
                .andExpect(jsonPath("$.total_amount").value(3800.00));
    }

    @Test
    void sixSeatsInOneBooking_isAllowed_andGroupDiscountApplies() throws Exception {
        Event event = saveEvent("Big Group", inDays(10), 200, "1000");

        book(event, "a@example.com", 1, 2, 3, 4, 5, 6)
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.seats").value(6))
                .andExpect(jsonPath("$.total_amount").value(5700.00));
    }

    @Test
    void eventToday_isStillBookable() throws Exception {
        Event event = saveEvent("Tonight", inDays(0), 200, "1500");

        book(event, "a@example.com", 1)
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.price_per_seat").value(1500.00));
    }

    @Test
    void responseSeatNumbers_areSorted_whateverOrderWasRequested() throws Exception {
        Event event = saveEvent("Sorted", inDays(10), 200, "1000");

        book(event, "a@example.com", 15, 12, 14, 13)
                .andExpect(jsonPath("$.seat_numbers", contains(12, 13, 14, 15)));
    }

    @Test
    void lastSeat_equalToTotalSeats_isAllowed() throws Exception {
        Event event = saveEvent("Tiny", inDays(10), 20, "1000");

        assertConfirmed(book(event, "a@example.com", 20));
    }

    // =====================================================================
    // API 3 - rejected bookings (201 + REJECTED)
    // =====================================================================

    @Test
    void pastEvent_is201_REJECTED_withDateReason() throws Exception {
        Event event = saveEvent("Old Show", inDays(-1), 200, "1500");

        assertRejected(book(event, "a@example.com", 1, 2), "Event is already over");

        assertEquals(1, bookingRepository.count());    // rejected bookings are saved too
    }

    @Test
    void takenSeats_areListedInAscendingOrder() throws Exception {
        Event event = saveEvent("Busy Show", inDays(10), 200, "1000");
        assertConfirmed(book(event, "a@example.com", 12, 14));

        assertRejected(book(event, "b@example.com", 16, 14, 12), "Seats already booked: 12, 14");
    }

    @Test
    void rejectedBooking_doesNotHoldItsSeats() throws Exception {
        Event event = saveEvent("Seat Release", inDays(10), 200, "1000");
        assertConfirmed(book(event, "a@example.com", 1, 2));
        assertRejected(book(event, "b@example.com", 2, 3), "Seats already booked: 2");   // asked for 3 too

        assertConfirmed(book(event, "c@example.com", 3));      // 3 was never held
    }

    @Test
    void perCustomerLimit_4Seats_then3More_isRejected() throws Exception {
        Event event = saveEvent("Limit", inDays(10), 200, "1000");
        assertConfirmed(book(event, "priya@example.com", 1, 2, 3, 4));

        assertRejected(book(event, "priya@example.com", 5, 6, 7), MAX_REASON);
    }

    @Test
    void perCustomerLimit_isExactlySix() throws Exception {
        Event event = saveEvent("Limit Six", inDays(10), 200, "1000");
        assertConfirmed(book(event, "priya@example.com", 1, 2, 3, 4));
        assertConfirmed(book(event, "priya@example.com", 5, 6));              // 4 + 2 = 6

        assertRejected(book(event, "priya@example.com", 7), MAX_REASON);      // 7th seat
    }

    @Test
    void perCustomerLimit_emailIsCaseInsensitive() throws Exception {
        Event event = saveEvent("Case", inDays(10), 200, "1000");
        assertConfirmed(book(event, "Priya@Example.com", 1, 2, 3, 4));

        assertRejected(book(event, "priya@example.COM", 5, 6, 7), MAX_REASON);
    }

    @Test
    void perCustomerLimit_isPerEvent() throws Exception {
        Event a = saveEvent("Event A", inDays(10), 200, "1000");
        Event b = saveEvent("Event B", inDays(10), 200, "1000");

        assertConfirmed(book(a, "priya@example.com", 1, 2, 3, 4, 5, 6));
        assertConfirmed(book(b, "priya@example.com", 1, 2, 3, 4, 5, 6));
    }

    @Test
    void perCustomerLimit_ignoresRejectedBookings() throws Exception {
        Event event = saveEvent("Rejected Not Counted", inDays(10), 200, "1000");
        assertConfirmed(book(event, "priya@example.com", 1, 2, 3, 4));
        assertRejected(book(event, "priya@example.com", 5, 6, 7), MAX_REASON);    // 3 rejected seats

        assertConfirmed(book(event, "priya@example.com", 5, 6));                  // 4 + 2 still fits
    }

    @Test
    void availabilityRule_isCheckedBeforePerCustomerRule() throws Exception {
        Event event = saveEvent("Order", inDays(10), 200, "1000");
        assertConfirmed(book(event, "priya@example.com", 1, 2, 3, 4, 5));

        // 5 held + 2 more would break the limit AND seat 5 is taken: availability wins
        assertRejected(book(event, "priya@example.com", 5, 6), "Seats already booked: 5");
    }

    // =====================================================================
    // API 3 - 404 and seat range
    // =====================================================================

    @Test
    void unknownEvent_returns404_withSpecBody_andSavesNothing() throws Exception {
        postJson("/api/bookings", bookingBody("event_id", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Event with id 999 not found"));

        assertEquals(0, bookingRepository.count());
    }

    @Test
    void seatAboveTotalSeats_returns400_withSeatMessage_andSavesNothing() throws Exception {
        Event event = saveEvent("Two Hundred", inDays(10), 200, "1000");

        book(event, "a@example.com", 250)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details", hasSize(1)))
                .andExpect(jsonPath("$.details[0].field").value("seat_numbers"))
                .andExpect(jsonPath("$.details[0].message").value("seat 250 does not exist for this event"));

        assertEquals(0, bookingRepository.count());
    }

    @Test
    void seatRange_boundary_201isTheLastInvalidSeat() throws Exception {
        Event event = saveEvent("Boundary", inDays(10), 200, "1000");

        assertConfirmed(book(event, "a@example.com", 200));
        book(event, "b@example.com", 201)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].message").value("seat 201 does not exist for this event"));
    }

    // =====================================================================
    // API 3 - validation (400 VALIDATION_FAILED)
    // =====================================================================

    @ParameterizedTest(name = "{0} <- {1}")
    @MethodSource("invalidFields")
    void invalidField_returns400_namingThatField_andSavesNothing(String field, Object value) throws Exception {
        postJson("/api/bookings", bookingBody(field, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[?(@.field=='" + field + "')]").exists());

        assertEquals(0, bookingRepository.count());
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                // event_id
                Arguments.of("event_id", null),
                Arguments.of("event_id", JSON_NULL),
                Arguments.of("event_id", 0),
                Arguments.of("event_id", -1),
                // customer_name
                Arguments.of("customer_name", "AB"),
                Arguments.of("customer_name", ""),
                Arguments.of("customer_name", "   "),
                Arguments.of("customer_name", "Priya2"),
                Arguments.of("customer_name", "Priya_Raman"),
                Arguments.of("customer_name", "A".repeat(51)),
                Arguments.of("customer_name", null),
                // customer_email
                Arguments.of("customer_email", "priya@"),
                Arguments.of("customer_email", "priya"),
                Arguments.of("customer_email", "@example.com"),
                Arguments.of("customer_email", "priya@example"),
                Arguments.of("customer_email", null),
                // seat_numbers
                Arguments.of("seat_numbers", List.of()),
                Arguments.of("seat_numbers", List.of(1, 2, 3, 4, 5, 6, 7)),
                Arguments.of("seat_numbers", List.of(5, 5)),
                Arguments.of("seat_numbers", List.of(0)),
                Arguments.of("seat_numbers", List.of(-3)),
                Arguments.of("seat_numbers", Arrays.asList(1, null)),
                Arguments.of("seat_numbers", null));
    }

    @Test
    void allFailingFields_areListedTogether_sortedByField() throws Exception {
        postJson("/api/bookings", bookingBody("customer_name", "AB", "customer_email", "priya@", "seat_numbers", List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details", hasSize(3)))
                .andExpect(jsonPath("$.details[*].field", contains("customer_email", "customer_name", "seat_numbers")));
    }

    @Test
    void validation_runsBefore_eventLookup() throws Exception {
        postJson("/api/bookings", bookingBody("event_id", 999, "seat_numbers", List.of(5, 5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void malformedJson_returns400_INVALID_REQUEST() throws Exception {
        postJson("/api/bookings", "{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void emptyBody_returns400_INVALID_REQUEST() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/bookings")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void wrongJsonTypes_return400_INVALID_REQUEST() throws Exception {
        postJson("/api/bookings", bookingBody("event_id", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
        postJson("/api/bookings", bookingBody("seat_numbers", "12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    // =====================================================================
    // API 4 - get booking
    // =====================================================================

    @Test
    void get_confirmedBooking_returnsSameShape_withSortedSeats() throws Exception {
        Event event = saveEvent("Chennai Jazz Night", inDays(71), 200, "1500");
        String created = book(event, "priya@example.com", 15, 12, 14, 13)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        getJson("/api/bookings/" + id.longValue())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.longValue()))
                .andExpect(jsonPath("$.event_id").value(event.getId()))
                .andExpect(jsonPath("$.customer_name").value("Priya Raman"))
                .andExpect(jsonPath("$.customer_email").value("priya@example.com"))
                .andExpect(jsonPath("$.seat_numbers", contains(12, 13, 14, 15)))
                .andExpect(jsonPath("$.seats").value(4))
                .andExpect(jsonPath("$.price_per_seat").value(1282.50))
                .andExpect(jsonPath("$.total_amount").value(5130.00))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.rejection_reason").value(nullValue()))
                .andExpect(jsonPath("$.booked_on").value(inDays(0).toString()));
    }

    @Test
    void get_rejectedBooking_isReturnedToo() throws Exception {
        Event event = saveEvent("Old Show", inDays(-1), 200, "1500");
        String created = book(event, "priya@example.com", 3, 1)
                .andReturn().getResponse().getContentAsString();
        Number id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        getJson("/api/bookings/" + id.longValue())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejection_reason").value("Event is already over"))
                .andExpect(jsonPath("$.seat_numbers", contains(1, 3)))
                .andExpect(jsonPath("$.seats").value(2));
    }

    @Test
    void get_missing_returns404_withSpecBody() throws Exception {
        getJson("/api/bookings/999")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message", containsString("999")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "1.5", "7x"})
    void get_idNotAPositiveInteger_returns400_INVALID_REQUEST(String id) throws Exception {
        getJson("/api/bookings/" + id)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }
}
