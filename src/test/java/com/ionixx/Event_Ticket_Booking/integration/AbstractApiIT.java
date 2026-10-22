package com.ionixx.Event_Ticket_Booking.integration;

import com.ionixx.Event_Ticket_Booking.entity.Event;
import com.ionixx.Event_Ticket_Booking.enums.Category;
import com.ionixx.Event_Ticket_Booking.filter.RequestIdFilter;
import com.ionixx.Event_Ticket_Booking.repository.BookingRepository;
import com.ionixx.Event_Ticket_Booking.repository.BookingSeatRepository;
import com.ionixx.Event_Ticket_Booking.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Boots the full application against in-memory H2 (profile "test") and drives it through MockMvc.
 * MockMvc is built by hand (webAppContextSetup) so RequestIdFilter is part of every request,
 * exactly as in production, and no Boot test-autoconfigure package is needed.
 */
@SpringBootTest
@ActiveProfiles("test")
abstract class AbstractApiIT {

    /** Use as a value in {@link #eventBody} / {@link #bookingBody} to send an explicit JSON null. */
    protected static final Object JSON_NULL = new Object() {
        @Override public String toString() { return "null"; }
    };

    @Autowired protected WebApplicationContext wac;
    @Autowired protected RequestIdFilter requestIdFilter;
    @Autowired protected EventRepository eventRepository;
    @Autowired protected BookingRepository bookingRepository;
    @Autowired protected BookingSeatRepository bookingSeatRepository;

    protected MockMvc mvc;

    @BeforeEach
    void resetState() {
        mvc = MockMvcBuilders.webAppContextSetup(wac).addFilters(requestIdFilter).build();
        bookingRepository.deleteAll();   // cascades to booking_seats
        eventRepository.deleteAll();
    }

    // ---------- requests ----------

    protected ResultActions postJson(String url, String body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions getJson(String url) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(url).accept(MediaType.APPLICATION_JSON));
    }

    // ---------- JSON bodies (no Jackson needed) ----------

    /** Valid event body; pass key/value pairs to override. A null value omits the field. */
    protected static String eventBody(Object... overrides) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "Chennai Jazz Night");
        m.put("venue", "Music Academy, Chennai");
        m.put("category", "MUSIC");
        m.put("event_date", LocalDate.now().plusDays(60).toString());
        m.put("total_seats", 200);
        m.put("base_price", 1500);
        applyOverrides(m, overrides);
        return render(m);
    }

    /** Valid booking body for event 1; pass key/value pairs to override. A null value omits the field. */
    protected static String bookingBody(Object... overrides) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("event_id", 1);
        m.put("customer_name", "Priya Raman");
        m.put("customer_email", "priya@example.com");
        m.put("seat_numbers", List.of(12, 13, 14, 15));
        applyOverrides(m, overrides);
        return render(m);
    }

    private static void applyOverrides(Map<String, Object> m, Object[] overrides) {
        for (int i = 0; i < overrides.length; i += 2) {
            m.put((String) overrides[i], overrides[i + 1]);
        }
    }

    private static String render(Map<String, Object> m) {
        StringJoiner sj = new StringJoiner(",", "{", "}");
        m.forEach((k, v) -> {
            if (v != null) {
                sj.add("\"" + k + "\":" + (v instanceof CharSequence ? "\"" + v + "\"" : v));
            }
        });
        return sj.toString();
    }

    // ---------- test data (straight into the DB, bypasses API validation) ----------

    protected Event saveEvent(String name, LocalDate date, int totalSeats, String basePrice) {
        Event e = new Event();
        e.setName(name);
        e.setCategory(Category.MUSIC);
        e.setBasePrice(new BigDecimal(basePrice));
        e.setTotalSeats(totalSeats);
        e.setEventDate(date);
        e.setVenue("Music Academy, Chennai");
        return eventRepository.saveAndFlush(e);
    }

    protected static LocalDate inDays(int days) {
        return LocalDate.now().plusDays(days);
    }
}
