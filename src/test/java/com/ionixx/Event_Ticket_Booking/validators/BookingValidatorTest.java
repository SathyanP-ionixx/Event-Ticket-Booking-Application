package com.ionixx.Event_Ticket_Booking.validators;

import com.ionixx.Event_Ticket_Booking.dto.BookingRequest;
import com.ionixx.Event_Ticket_Booking.dto.ErrorResponse;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.BookingValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookingValidatorTest {

    private final BookingValidator validator = new BookingValidator();

    private static BookingRequest valid() {
        BookingRequest r = new BookingRequest();
        r.setEventId(1L);
        r.setCustomerName("Priya Raman");
        r.setCustomerEmail("priya@example.com");
        r.setSeatNumbers(List.of(12, 13, 14, 15));
        return r;
    }

    private List<String> failingFields(BookingRequest req) {
        BookingValidationException ex = assertThrows(BookingValidationException.class, () -> validator.validate(req));
        return ex.getDetails().stream().map(ErrorResponse.FieldDetail::field).toList();
    }

    // ---- happy paths ----

    @Test
    void validRequest_passes() {
        assertDoesNotThrow(() -> validator.validate(valid()));
    }

    @Test
    void sixSeats_isTheAllowedMaximum() {
        BookingRequest r = valid();
        r.setSeatNumbers(List.of(1, 2, 3, 4, 5, 6));
        assertDoesNotThrow(() -> validator.validate(r));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Abc", "Priya Raman", "A B"})
    void validNames_pass(String name) {
        BookingRequest r = valid();
        r.setCustomerName(name);
        assertDoesNotThrow(() -> validator.validate(r));
    }

    @Test
    void fiftyCharName_passes_fiftyOne_fails() {
        BookingRequest r = valid();
        r.setCustomerName("A".repeat(50));
        assertDoesNotThrow(() -> validator.validate(r));
        r.setCustomerName("A".repeat(51));
        assertEquals(List.of("customer_name"), failingFields(r));
    }

    @ParameterizedTest
    @ValueSource(strings = {"priya@example.com", "p.r+tag@sub.example.co.in", "A_B-c%d@mail.example.org"})
    void validEmails_pass(String email) {
        BookingRequest r = valid();
        r.setCustomerEmail(email);
        assertDoesNotThrow(() -> validator.validate(r));
    }

    // ---- body ----

    @Test
    void nullBody_failsOnBody() {
        assertEquals(List.of("body"), failingFields(null));
    }

    // ---- event_id ----

    @Test
    void missingEventId_fails() {
        BookingRequest r = valid();
        r.setEventId(null);
        assertEquals(List.of("event_id"), failingFields(r));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -100})
    void nonPositiveEventId_fails(long id) {
        BookingRequest r = valid();
        r.setEventId(id);
        assertEquals(List.of("event_id"), failingFields(r));
    }

    // ---- customer_name ----

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"AB", "a", "   ", "Priya2", "Priya_Raman", "Priya-Raman", "Priya.R", "Pri\u00e9ya"})
    void invalidNames_fail(String name) {
        BookingRequest r = valid();
        r.setCustomerName(name);
        assertEquals(List.of("customer_name"), failingFields(r));
    }

    // ---- customer_email ----

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"priya@", "priya", "@example.com", "priya@example", "priya @example.com", "priya@@example.com", "priya@example.c"})
    void invalidEmails_fail(String email) {
        BookingRequest r = valid();
        r.setCustomerEmail(email);
        assertEquals(List.of("customer_email"), failingFields(r));
    }

    @Test
    void emailLongerThan255_fails() {
        BookingRequest r = valid();
        r.setCustomerEmail("a".repeat(250) + "@example.com");
        assertEquals(List.of("customer_email"), failingFields(r));
    }

    // ---- seat_numbers ----

    @Test
    void nullSeatList_fails() {
        BookingRequest r = valid();
        r.setSeatNumbers(null);
        assertEquals(List.of("seat_numbers"), failingFields(r));
    }

    @Test
    void emptySeatList_fails() {
        BookingRequest r = valid();
        r.setSeatNumbers(List.of());
        assertEquals(List.of("seat_numbers"), failingFields(r));
    }

    @Test
    void sevenSeats_fails() {
        BookingRequest r = valid();
        r.setSeatNumbers(List.of(1, 2, 3, 4, 5, 6, 7));
        assertEquals(List.of("seat_numbers"), failingFields(r));
    }

    @Test
    void zeroNegativeOrNullSeat_fails() {
        for (List<Integer> seats : List.of(List.of(0), List.of(-3), Arrays.asList(1, null), List.of(1, 2, 0))) {
            BookingRequest r = valid();
            r.setSeatNumbers(seats);
            assertEquals(List.of("seat_numbers"), failingFields(r), "seats=" + seats);
        }
    }

    @Test
    void duplicateSeats_fail() {
        BookingRequest r = valid();
        r.setSeatNumbers(List.of(5, 5));
        BookingValidationException ex = assertThrows(BookingValidationException.class, () -> validator.validate(r));
        assertEquals("seat_numbers", ex.getDetails().get(0).field());
        assertEquals("must not contain duplicates", ex.getDetails().get(0).message());
    }

    // ---- all failures are reported together ----

    @Test
    void everyFailingField_isListed_sortedByField_oneMessagePerField() {
        BookingRequest r = new BookingRequest();
        r.setEventId(0L);
        r.setCustomerName("AB");
        r.setCustomerEmail("priya@");
        r.setSeatNumbers(List.of(5, 5, 0));   // two problems on one field -> still one entry
        assertEquals(List.of("customer_email", "customer_name", "event_id", "seat_numbers"), failingFields(r));
    }

    @Test
    void emptyObject_listsAllFourFields_asRequired() {
        BookingValidationException ex = assertThrows(BookingValidationException.class,
                () -> validator.validate(new BookingRequest()));
        assertEquals(List.of("customer_email", "customer_name", "event_id", "seat_numbers"),
                ex.getDetails().stream().map(ErrorResponse.FieldDetail::field).toList());
        ex.getDetails().forEach(d -> assertEquals("is required", d.message()));
    }
}
