package com.ionixx.Event_Ticket_Booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ionixx.Event_Ticket_Booking.entity.Event;
import com.ionixx.Event_Ticket_Booking.enums.Category;
import com.ionixx.Event_Ticket_Booking.repository.EventRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Booking rules, pricing, seat map, reports: the whole flow through HTTP, security and a real
 * database.
 */
class BookingFlowTest extends AbstractIntegrationTest {

  @Autowired EventRepository eventRepository;

  // ------------------------------------------------------------------ booking + pricing

  @Test
  void confirmedBooking_isPriced_withEarlyBirdDiscount() throws Exception {
    Account organiser = organiser();
    long eventId = createEvent(organiser, 10); // 45 days away, base price 100.00
    Account customer = customer();

    String body =
        book(customer, eventId, "John Doe", "john@example.com", 2, 1)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.event_id").value(eventId))
            .andExpect(jsonPath("$.seats").value(2))
            .andExpect(jsonPath("$.seat_numbers[0]").value(1))
            .andExpect(jsonPath("$.seat_numbers[1]").value(2))
            .andExpect(jsonPath("$.rejection_reason").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertAmount(body, "$.price_per_seat", "90.00");
    assertAmount(body, "$.total_amount", "180.00");
  }

  @Test
  void groupBooking_stacksTheGroupDiscount() throws Exception {
    long eventId = createEvent(organiser(), 20);

    String body = bookOk(customer(), eventId, "group@example.com", 1, 2, 3, 4);

    assertAmount(body, "$.price_per_seat", "85.50");
    assertAmount(body, "$.total_amount", "342.00");
  }

  @Test
  void lateBooking_hasNoEarlyBirdDiscount() throws Exception {
    long eventId = createEvent(organiser(), 10, 5, "100.00");

    String body = bookOk(customer(), eventId, "late@example.com", 1);

    assertAmount(body, "$.price_per_seat", "100.00");
  }

  @Test
  void takenSeats_areRejected_butTheAttemptIsStored() throws Exception {
    long eventId = createEvent(organiser(), 10);
    bookOk(customer(), eventId, "first@example.com", 1, 2);

    String body =
        book(customer(), eventId, "Second Person", "second@example.com", 2, 3, 1)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.rejection_reason").value("Seats already booked: 1, 2"))
            .andExpect(jsonPath("$.price_per_seat").doesNotExist())
            .andExpect(jsonPath("$.total_amount").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();

    getBooking(customer(), bookingId(body)).andExpect(status().isNotFound()); // someone else's
  }

  @Test
  void sixSeatLimit_isPerCustomerEmail_caseInsensitive() throws Exception {
    long eventId = createEvent(organiser(), 20);
    Account customer = customer();
    bookOk(customer, eventId, "Limit@Example.com", 1, 2, 3, 4);

    book(customer, eventId, "John Doe", "limit@example.com", 5, 6, 7)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(
            jsonPath("$.rejection_reason")
                .value("Customer already holds the maximum seats for this event"));

    book(customer, eventId, "John Doe", "limit@example.com", 5, 6)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
  }

  @Test
  void pastEvent_isRejected() throws Exception {
    Account organiser = organiser();
    Event past = new Event();
    past.setName(uniqueEventName());
    past.setCategory(Category.TECH);
    past.setBasePrice(new BigDecimal("50.00"));
    past.setTotalSeats(20);
    past.setEventDate(LocalDate.now().minusDays(1));
    past.setVenue("Old Hall");
    past.setOrganiserId(organiser.id());
    long eventId = eventRepository.save(past).getId();

    book(customer(), eventId, "John Doe", "late@example.com", 1)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.rejection_reason").value("Event is already over"));
  }

  // ------------------------------------------------------------------ booking validation

  @Test
  void seatBeyondTheVenue_is400() throws Exception {
    long eventId = createEvent(organiser(), 10);

    book(customer(), eventId, "John Doe", "john@example.com", 11)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.details[0].field").value("seat_numbers"))
        .andExpect(jsonPath("$.details[0].message").value("seat 11 does not exist for this event"));
  }

  @Test
  void unknownEvent_is404() throws Exception {
    book(customer(), MISSING_ID, "John Doe", "john@example.com", 1)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  @Test
  void emptyBooking_listsEveryRequiredField() throws Exception {
    mvc.perform(
            as(post("/api/bookings"), customer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.details.length()").value(4))
        .andExpect(jsonPath("$.details[0].field").value("customer_email"))
        .andExpect(jsonPath("$.details[1].field").value("customer_name"))
        .andExpect(jsonPath("$.details[2].field").value("event_id"))
        .andExpect(jsonPath("$.details[3].field").value("seat_numbers"));
  }

  @Test
  void wrongJsonTypes_andMissingBody_are400() throws Exception {
    Account customer = customer();

    mvc.perform(
            as(post("/api/bookings"), customer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event_id\":\"abc\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));

    mvc.perform(as(post("/api/bookings"), customer).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Request body is required"));
  }

  // ------------------------------------------------------------------ get booking

  @Test
  void owner_readsTheirBooking_withTheSeatNumbers() throws Exception {
    long eventId = createEvent(organiser(), 10);
    Account customer = customer();
    long bookingId = bookingId(bookOk(customer, eventId, "john@example.com", 3, 4));

    getBooking(customer, bookingId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(bookingId))
        .andExpect(jsonPath("$.customer_email").value("john@example.com"))
        .andExpect(jsonPath("$.seat_numbers.length()").value(2))
        .andExpect(jsonPath("$.booked_on").value(LocalDate.now().toString()));
  }

  @Test
  void missingAndMalformedBookingIds() throws Exception {
    Account customer = customer();

    getBooking(customer, MISSING_ID)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Booking not found with id " + MISSING_ID));
    for (String bad : new String[] {"abc", "0", "-4"}) {
      getBooking(customer, bad)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }
  }

  // ------------------------------------------------------------------ seat map

  @Test
  void seatMap_reflectsOnlyConfirmedBookings() throws Exception {
    long eventId = createEvent(organiser(), 10);
    Account customer = customer();
    bookOk(customer, eventId, "a@example.com", 1, 2);
    bookOk(customer(), eventId, "b@example.com", 2, 3); // rejected: seat 2 is taken

    mvc.perform(as(get("/api/events/" + eventId + "/seats"), customer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.event_id").value(eventId))
        .andExpect(jsonPath("$.total_seats").value(10))
        .andExpect(jsonPath("$.booked_seats.length()").value(2))
        .andExpect(jsonPath("$.available_seats.length()").value(8))
        .andExpect(jsonPath("$.available_seats[0]").value(3));
  }

  @Test
  void seatMap_unknownEvent_is404() throws Exception {
    mvc.perform(as(get("/api/events/" + MISSING_ID + "/seats"), customer()))
        .andExpect(status().isNotFound());
  }

  // ------------------------------------------------------------------ organiser reports

  @Test
  void summary_totalsConfirmedAndRejected() throws Exception {
    Account organiser = organiser();
    long eventId = createEvent(organiser, 10);
    bookOk(customer(), eventId, "a@example.com", 1, 2, 3, 4, 5); // 5 x 85.50 = 427.50
    bookOk(customer(), eventId, "b@example.com", 5); // rejected

    String body =
        mvc.perform(as(get("/api/events/" + eventId + "/summary"), organiser))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.event_id").value(eventId))
            .andExpect(jsonPath("$.total_seats").value(10))
            .andExpect(jsonPath("$.seats_sold").value(5))
            .andExpect(jsonPath("$.seats_available").value(5))
            .andExpect(jsonPath("$.confirmed_bookings").value(1))
            .andExpect(jsonPath("$.rejected_bookings").value(1))
            .andExpect(jsonPath("$.demand_band").value("MEDIUM"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertAmount(body, "$.total_revenue", "427.50");
    assertAmount(body, "$.occupancy_pct", "50.0");
  }

  @Test
  void summary_demandBands_lowAndHigh() throws Exception {
    Account organiser = organiser();

    long quiet = createEvent(organiser, 10);
    bookOk(customer(), quiet, "q@example.com", 1, 2, 3);
    mvc.perform(as(get("/api/events/" + quiet + "/summary"), organiser))
        .andExpect(jsonPath("$.demand_band").value("LOW"));

    long busy = createEvent(organiser, 10);
    bookOk(customer(), busy, "x@example.com", 1, 2, 3, 4);
    bookOk(customer(), busy, "y@example.com", 5, 6, 7, 8);
    mvc.perform(as(get("/api/events/" + busy + "/summary"), organiser))
        .andExpect(jsonPath("$.demand_band").value("HIGH"));
  }

  @Test
  void summary_emptyEvent_hasZeroRevenue() throws Exception {
    Account organiser = organiser();
    long eventId = createEvent(organiser, 10);

    String body =
        mvc.perform(as(get("/api/events/" + eventId + "/summary"), organiser))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.seats_sold").value(0))
            .andExpect(jsonPath("$.demand_band").value("LOW"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertAmount(body, "$.total_revenue", "0.00");
  }

  @Test
  void listBookings_isAPagedEnvelope_newestFirst_andFilterableByStatus() throws Exception {
    Account organiser = organiser();
    long eventId = createEvent(organiser, 10);
    long confirmed = bookingId(bookOk(customer(), eventId, "a@example.com", 1));
    long rejected = bookingId(bookOk(customer(), eventId, "b@example.com", 1));

    mvc.perform(as(get("/api/events/" + eventId + "/bookings"), organiser))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].id").value(rejected))
        .andExpect(jsonPath("$.content[1].id").value(confirmed))
        .andExpect(jsonPath("$.content[1].seat_numbers[0]").value(1))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.total_elements").value(2))
        .andExpect(jsonPath("$.total_pages").value(1));

    mvc.perform(as(get("/api/events/" + eventId + "/bookings?status=CONFIRMED"), organiser))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].id").value(confirmed))
        .andExpect(jsonPath("$.total_elements").value(1));

    mvc.perform(as(get("/api/events/" + eventId + "/bookings?status=REJECTED"), organiser))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].id").value(rejected));
  }

  @Test
  void listBookings_walksThroughThePages() throws Exception {
    Account organiser = organiser();
    long eventId = createEvent(organiser, 20);
    Account customer = customer();
    long[] ids = new long[5];
    for (int i = 0; i < 5; i++) {
      ids[i] = bookingId(bookOk(customer, eventId, "page" + i + "@example.com", i + 1));
    }
    // newest first: [4, 3] [2, 1] [0]

    mvc.perform(as(get("/api/events/" + eventId + "/bookings?page=0&size=2"), organiser))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].id").value(ids[4]))
        .andExpect(jsonPath("$.content[1].id").value(ids[3]))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(2))
        .andExpect(jsonPath("$.total_elements").value(5))
        .andExpect(jsonPath("$.total_pages").value(3));

    mvc.perform(as(get("/api/events/" + eventId + "/bookings?page=1&size=2"), organiser))
        .andExpect(jsonPath("$.content[0].id").value(ids[2]))
        .andExpect(jsonPath("$.content[1].id").value(ids[1]))
        .andExpect(jsonPath("$.page").value(1));

    mvc.perform(as(get("/api/events/" + eventId + "/bookings?page=2&size=2"), organiser))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].id").value(ids[0]));

    // beyond the last page: still 200, empty content, real totals
    mvc.perform(as(get("/api/events/" + eventId + "/bookings?page=3&size=2"), organiser))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0))
        .andExpect(jsonPath("$.total_elements").value(5))
        .andExpect(jsonPath("$.total_pages").value(3));
  }

  @Test
  void listBookings_rejectsBadPagingParameters() throws Exception {
    Account organiser = organiser();
    long eventId = createEvent(organiser, 10);
    String[][] bad = {
      {"page=-1", "page"},
      {"page=abc", "page"},
      {"size=0", "size"},
      {"size=101", "size"},
      {"size=x", "size"}
    };

    for (String[] q : bad) {
      mvc.perform(as(get("/api/events/" + eventId + "/bookings?" + q[0]), organiser))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.details[0].field").value(q[1]));
    }
  }

  @Test
  void listBookings_reportsEveryBadPagingParameterAtOnce() throws Exception {
    Account organiser = organiser();
    long eventId = createEvent(organiser, 10);

    mvc.perform(as(get("/api/events/" + eventId + "/bookings?page=-1&size=500"), organiser))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.length()").value(2))
        .andExpect(jsonPath("$.details[0].field").value("page"))
        .andExpect(jsonPath("$.details[1].field").value("size"));
  }

  @Test
  void listBookings_acceptsTheMaximumPageSize() throws Exception {
    Account organiser = organiser();
    long eventId = createEvent(organiser, 10);

    mvc.perform(as(get("/api/events/" + eventId + "/bookings?size=100"), organiser))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(100))
        .andExpect(jsonPath("$.content.length()").value(0))
        .andExpect(jsonPath("$.total_pages").value(0));
  }

  @Test
  void listBookings_pagingNeverBypassesOwnership() throws Exception {
    long eventId = createEvent(organiser(), 10);

    mvc.perform(as(get("/api/events/" + eventId + "/bookings?page=0&size=1"), organiser()))
        .andExpect(status().isForbidden());
  }

  @Test
  void listBookings_invalidStatus_is400() throws Exception {
    Account organiser = organiser();
    long eventId = createEvent(organiser, 10);

    for (String bad : new String[] {"PENDING", "confirmed"}) {
      mvc.perform(as(get("/api/events/" + eventId + "/bookings?status=" + bad), organiser))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.details[0].field").value("status"))
          .andExpect(jsonPath("$.details[0].message").value("must be CONFIRMED or REJECTED"));
    }
  }

  @Test
  void reports_forAnUnknownEvent_are404() throws Exception {
    Account organiser = organiser();

    mvc.perform(as(get("/api/events/" + MISSING_ID + "/summary"), organiser))
        .andExpect(status().isNotFound());
    mvc.perform(as(get("/api/events/" + MISSING_ID + "/bookings"), organiser))
        .andExpect(status().isNotFound());
  }
}
