package com.ionixx.Event_Ticket_Booking.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bean Validation rules on the event request (API 1). */
class EventRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    private static EventRequest valid() {
        EventRequest r = new EventRequest();
        r.setName("Chennai Jazz Night");
        r.setVenue("Music Academy, Chennai");
        r.setCategory("MUSIC");
        r.setEventDate(LocalDate.now().plusDays(30).toString());
        r.setTotalSeats(200);
        r.setBasePrice(new BigDecimal("1500"));
        return r;
    }

    private static Set<String> failing(EventRequest r) {
        return validator.validate(r).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static String messageFor(EventRequest r, String property) {
        return validator.validate(r).stream()
                .filter(v -> v.getPropertyPath().toString().equals(property))
                .map(ConstraintViolation::getMessage)
                .findFirst().orElseThrow();
    }

    @Test
    void validRequest_hasNoViolations() {
        assertTrue(failing(valid()).isEmpty());
    }

    // ---- name ----
    @Test
    void name_boundaries() {
        EventRequest r = valid();
        r.setName("Abc");
        assertTrue(failing(r).isEmpty());
        r.setName("x".repeat(100));
        assertTrue(failing(r).isEmpty());
        r.setName("AB");
        assertEquals(Set.of("name"), failing(r));
        r.setName("x".repeat(101));
        assertEquals(Set.of("name"), failing(r));
    }

    @Test
    void name_missing_isRequired() {
        EventRequest r = valid();
        r.setName(null);
        assertEquals(Set.of("name"), failing(r));
        assertEquals("is required", messageFor(r, "name"));
    }

    @Test
    void name_isTrimmed_soWhitespaceOnlyFails_andPaddingDoesNotCount() {
        EventRequest r = valid();
        r.setName("   ");
        assertEquals(Set.of("name"), failing(r));

        r.setName("  AB  ");                       // trims to 2 chars
        assertEquals(Set.of("name"), failing(r));

        r.setName("  Jazz Night  ");
        assertEquals("Jazz Night", r.getName());
        assertTrue(failing(r).isEmpty());
    }

    // ---- venue ----
    @Test
    void venue_boundaries_andTrim() {
        EventRequest r = valid();
        r.setVenue("abc");
        assertTrue(failing(r).isEmpty());
        r.setVenue("ab");
        assertEquals(Set.of("venue"), failing(r));
        r.setVenue("v".repeat(101));
        assertEquals(Set.of("venue"), failing(r));
        r.setVenue("  Hall  ");
        assertEquals("Hall", r.getVenue());
        r.setVenue(null);
        assertEquals(Set.of("venue"), failing(r));
    }

    // ---- category ----
    @ParameterizedTest
    @ValueSource(strings = {"MUSIC", "SPORTS", "TECH", "THEATRE", "music", "Sports", "tEcH", " theatre "})
    void category_validValues_caseInsensitive(String category) {
        EventRequest r = valid();
        r.setCategory(category);
        assertTrue(failing(r).isEmpty(), category);
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMEDY", "", "MUSICAL", "MUSIC SPORTS", "music|sports"})
    void category_invalidValues(String category) {
        EventRequest r = valid();
        r.setCategory(category);
        assertEquals(Set.of("category"), failing(r), category);
        assertEquals("must be one of MUSIC, SPORTS, TECH, THEATRE", messageFor(r, "category"));
    }

    @Test
    void category_missing() {
        EventRequest r = valid();
        r.setCategory(null);
        assertEquals(Set.of("category"), failing(r));
    }

    // ---- base_price ----
    @ParameterizedTest
    @ValueSource(strings = {"0.01", "1", "1500", "1500.5", "1500.50", "9999999999.99"})
    void basePrice_valid(String price) {
        EventRequest r = valid();
        r.setBasePrice(new BigDecimal(price));
        assertTrue(failing(r).isEmpty(), price);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1", "-0.01"})
    void basePrice_mustBeGreaterThanZero(String price) {
        EventRequest r = valid();
        r.setBasePrice(new BigDecimal(price));
        assertEquals(Set.of("basePrice"), failing(r), price);
        assertEquals("must be greater than 0", messageFor(r, "basePrice"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.234", "10000000000", "12345678901.00"})
    void basePrice_tooManyDigits(String price) {
        EventRequest r = valid();
        r.setBasePrice(new BigDecimal(price));
        assertEquals(Set.of("basePrice"), failing(r), price);
    }

    @Test
    void basePrice_missing() {
        EventRequest r = valid();
        r.setBasePrice(null);
        assertEquals(Set.of("basePrice"), failing(r));
    }

    // ---- total_seats ----
    @ParameterizedTest
    @ValueSource(ints = {10, 11, 200, 9999, 10000})
    void totalSeats_valid(int seats) {
        EventRequest r = valid();
        r.setTotalSeats(seats);
        assertTrue(failing(r).isEmpty(), "seats=" + seats);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 5, 9, 10001, 100000})
    void totalSeats_outOfRange(int seats) {
        EventRequest r = valid();
        r.setTotalSeats(seats);
        assertEquals(Set.of("totalSeats"), failing(r), "seats=" + seats);
        assertEquals("must be between 10 and 10000", messageFor(r, "totalSeats"));
    }

    @Test
    void totalSeats_missing() {
        EventRequest r = valid();
        r.setTotalSeats(null);
        assertEquals(Set.of("totalSeats"), failing(r));
    }

    // ---- event_date ----
    @Test
    void eventDate_rules() {
        EventRequest r = valid();
        r.setEventDate(null);
        assertEquals(Set.of("eventDate"), failing(r));

        r.setEventDate(LocalDate.now().toString());
        assertEquals(Set.of("eventDate"), failing(r));
        assertEquals("must be a future date", messageFor(r, "eventDate"));

        r.setEventDate(LocalDate.now().minusDays(1).toString());
        assertEquals(Set.of("eventDate"), failing(r));

        r.setEventDate("20-12-2099");
        assertEquals(Set.of("eventDate"), failing(r));
        assertEquals("must be in format yyyy-mm-dd", messageFor(r, "eventDate"));

        r.setEventDate("  " + LocalDate.now().plusDays(1) + "  ");    // trimmed on the way in
        assertTrue(failing(r).isEmpty());
    }

    // ---- several failures at once ----
    @Test
    void multipleInvalidFields_areAllReported() {
        EventRequest r = valid();
        r.setCategory("COMEDY");
        r.setTotalSeats(5);
        assertEquals(Set.of("category", "totalSeats"), failing(r));
    }

    @Test
    void emptyRequest_reportsAllSixFields() {
        assertEquals(Set.of("name", "venue", "category", "eventDate", "totalSeats", "basePrice"),
                failing(new EventRequest()));
    }
}
