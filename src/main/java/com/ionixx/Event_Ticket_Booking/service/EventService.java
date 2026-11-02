package com.ionixx.Event_Ticket_Booking.service;

import com.ionixx.Event_Ticket_Booking.dto.EventRequest;
import com.ionixx.Event_Ticket_Booking.dto.EventResponse;
import com.ionixx.Event_Ticket_Booking.entity.Event;
import com.ionixx.Event_Ticket_Booking.enums.Category;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.*;
import com.ionixx.Event_Ticket_Booking.repository.BookingSeatRepository;
import com.ionixx.Event_Ticket_Booking.repository.EventRepository;
import com.ionixx.Event_Ticket_Booking.security.AuthUser;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** No logging here on purpose: RequestIdFilter writes the one log line per request. */
@Service
public class EventService {

  private final EventRepository repository;
  private final BookingSeatRepository bookingSeatRepository;

  public EventService(EventRepository repository, BookingSeatRepository bookingSeatRepository) {
    this.repository = repository;
    this.bookingSeatRepository = bookingSeatRepository;
  }

  @Transactional
  public EventResponse create(EventRequest req, Long organiserId) {
    LocalDate date = LocalDate.parse(req.getEventDate());

    // friendly fast path; the unique constraint (uk_event_name_date) is the real guarantee
    repository
        .findByNameAndEventDate(req.getName(), date)
        .ifPresent(
            existing -> {
              throw new DuplicateEventException(existing.getId());
            });

    Event event = new Event();
    event.setName(req.getName());
    event.setCategory(Category.valueOf(req.getCategory().toUpperCase(Locale.ROOT)));
    event.setBasePrice(req.getBasePrice());
    event.setTotalSeats(req.getTotalSeats());
    event.setEventDate(date);
    event.setVenue(req.getVenue());
    event.setOrganiserId(organiserId);
    // flush so a concurrent duplicate fails here and is mapped to 409 by the handler
    return EventResponse.from(repository.saveAndFlush(event));
  }

  @Transactional(readOnly = true)
  public EventResponse getById(long id) {
    return repository
        .findById(id)
        .map(EventResponse::from)
        .orElseThrow(() -> new EventNotFoundException(id));
  }

  @Transactional(readOnly = true)
  public List<EventResponse> fetchAllFutureEvents() {
    return null;
  }

  @Transactional
  public EventResponse update(long id, EventRequest req, AuthUser caller) {
    Event event =
        repository.findByIdForUpdate(id).orElseThrow(() -> new EventNotFoundException(id));
    requireOwner(event, caller);

    if (event.getEventDate().isBefore(LocalDate.now())) { // same definition of "over" as booking
      throw new EventAlreadyOverException();
    }

    LocalDate date = LocalDate.parse(req.getEventDate());

    Integer highestBooked = bookingSeatRepository.findMaxConfirmedSeatNumber(id);
    if (highestBooked != null && req.getTotalSeats() < highestBooked) {
      throw new BookingValidationException(
          "total_seats",
          "must be at least " + highestBooked + " (seat " + highestBooked + " is already booked)");
    }

    // friendly fast path; the unique constraint is still the real guarantee (see saveAndFlush
    // below)
    repository
        .findByNameAndEventDate(req.getName(), date)
        .filter(other -> !other.getId().equals(id))
        .ifPresent(
            other -> {
              throw new DuplicateEventException(other.getId());
            });

    event.setName(req.getName());
    event.setCategory(Category.valueOf(req.getCategory().toUpperCase(Locale.ROOT)));
    event.setBasePrice(req.getBasePrice());
    event.setTotalSeats(req.getTotalSeats());
    event.setEventDate(date);
    event.setVenue(req.getVenue());
    // flush so a concurrent duplicate fails here and is mapped to 409 by the handler
    return EventResponse.from(repository.saveAndFlush(event));
  }

  private void requireOwner(Event event, AuthUser caller) {
    if (event.getOrganiserId() == null || !event.getOrganiserId().equals(caller.id())) {
      throw new AccessDeniedException("Not the organiser of this event");
    }
  }
}
