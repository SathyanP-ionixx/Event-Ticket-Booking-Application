package com.ionixx.Event_Ticket_Booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ionixx.Event_Ticket_Booking.entity.Booking;
import com.ionixx.Event_Ticket_Booking.entity.Event;
import com.ionixx.Event_Ticket_Booking.enums.Category;
import com.ionixx.Event_Ticket_Booking.repository.BookingRepository;
import com.ionixx.Event_Ticket_Booking.repository.EventRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Resource-level authorization: a role is not enough, the caller must also own the event or
 * booking.
 */
class OwnershipAuthorizationTest extends AbstractIntegrationTest {

  @Autowired EventRepository eventRepository;
  @Autowired BookingRepository bookingRepository;

  // ------------------------------------------------------------------ events

  @Test
  void organiser_canSeeTheirOwnEventReports() throws Exception {
    Account a = organiser();
    long eventId = createEvent(a, 10);

    mvc.perform(as(get("/api/events/" + eventId + "/summary"), a)).andExpect(status().isOk());
    mvc.perform(as(get("/api/events/" + eventId + "/bookings"), a)).andExpect(status().isOk());
  }

  @Test
  void organiser_cannotSeeAnotherOrganisersSummaryOrBookings() throws Exception {
    Account a = organiser();
    Account b = organiser();
    long eventId = createEvent(a, 10);
    bookOk(customer(), eventId, "c@example.com", 1);

    mvc.perform(as(get("/api/events/" + eventId + "/summary"), b))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    mvc.perform(as(get("/api/events/" + eventId + "/bookings"), b))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    mvc.perform(as(get("/api/events/" + eventId + "/bookings?status=CONFIRMED"), b))
        .andExpect(status().isForbidden());
  }

  @Test
  void eventWithoutAnOwner_isNotReadableByAnyOrganiser() throws Exception {
    Event legacy = new Event();
    legacy.setName(uniqueEventName());
    legacy.setCategory(Category.SPORTS);
    legacy.setBasePrice(new BigDecimal("20.00"));
    legacy.setTotalSeats(30);
    legacy.setEventDate(LocalDate.now().plusDays(20));
    legacy.setVenue("Legacy Arena"); // organiserId stays null
    long eventId = eventRepository.save(legacy).getId();
    Account anyOrganiser = organiser();

    mvc.perform(as(get("/api/events/" + eventId + "/summary"), anyOrganiser))
        .andExpect(status().isForbidden());
    mvc.perform(as(get("/api/events/" + eventId + "/bookings"), anyOrganiser))
        .andExpect(status().isForbidden());
  }

  // ------------------------------------------------------------------ bookings

  @Test
  void booking_isVisibleToItsOwner_andToTheEventsOrganiserOnly() throws Exception {
    Account organiserA = organiser();
    Account organiserB = organiser();
    Account owner = customer();
    Account stranger = customer();
    long eventId = createEvent(organiserA, 10);
    long bookingId = bookingId(bookOk(owner, eventId, "owner@example.com", 1));

    getBooking(owner, bookingId).andExpect(status().isOk());
    getBooking(organiserA, bookingId).andExpect(status().isOk());

    // 404, not 403: other people cannot even tell the id exists
    getBooking(stranger, bookingId).andExpect(status().isNotFound());
    getBooking(organiserB, bookingId).andExpect(status().isNotFound());
  }

  @Test
  void legacyBookingWithoutAnOwner_isOnlyVisibleToTheEventsOrganiser() throws Exception {
    Account organiserA = organiser();
    long eventId = createEvent(organiserA, 10);

    Booking legacy = new Booking();
    legacy.setEventId(eventId); // userId stays null
    legacy.setCustomerName("Old Customer");
    legacy.setCustomerEmail("old@example.com");
    legacy.setSeats(1);
    legacy.setStatus("CONFIRMED");
    legacy.setBookedOn(LocalDate.now());
    legacy.addSeat(7);
    long bookingId = bookingRepository.save(legacy).getId();

    getBooking(customer(), bookingId).andExpect(status().isNotFound());
    getBooking(organiser(), bookingId).andExpect(status().isNotFound());
    getBooking(organiserA, bookingId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.seat_numbers[0]").value(7));
  }
}
