package com.ionixx.Event_Ticket_Booking.service;

import com.ionixx.Event_Ticket_Booking.dto.EventRequest;
import com.ionixx.Event_Ticket_Booking.dto.EventResponse;
import com.ionixx.Event_Ticket_Booking.entity.Event;
import com.ionixx.Event_Ticket_Booking.enums.Category;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.DuplicateEventException;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.EventNotFoundException;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.NoEventsFoundException;
import com.ionixx.Event_Ticket_Booking.repository.EventRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** No logging here on purpose: RequestIdFilter writes the one log line per request. */
@Service
public class EventService {

  private final EventRepository repository;

  public EventService(EventRepository repository) {
    this.repository = repository;
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
  public List<EventResponse> fetchAllEvents() {
    try {
      List<Event> events = repository.findAll();
      List<EventResponse> eventResponses = events.stream().map(EventResponse::from).toList();
      if (eventResponses.isEmpty()) {
        throw new NoEventsFoundException("Currently there is no Events Available");
      }
      return eventResponses;
    } catch (RuntimeException e) {
      throw new RuntimeException("Failed to Fetch Events");
    }
  }
}
