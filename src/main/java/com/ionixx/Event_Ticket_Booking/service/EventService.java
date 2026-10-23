package com.ionixx.Event_Ticket_Booking.service;

import com.ionixx.Event_Ticket_Booking.dto.EventRequest;
import com.ionixx.Event_Ticket_Booking.dto.EventResponse;
import com.ionixx.Event_Ticket_Booking.entity.Event;
import com.ionixx.Event_Ticket_Booking.enums.Category;
import com.ionixx.Event_Ticket_Booking.repository.EventRepository;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.DuplicateEventException;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.EventNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Locale;

/** No logging here on purpose: RequestIdFilter writes the one log line per request. */
@Service
public class EventService {

    private final EventRepository repository;

    public EventService(EventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public EventResponse create(EventRequest req) {
        LocalDate date = LocalDate.parse(req.getEventDate());

        // friendly fast path; the unique constraint (uk_event_name_date) is the real guarantee
        repository.findByNameAndEventDate(req.getName(), date).ifPresent(existing -> {
            throw new DuplicateEventException(existing.getId());
        });

        Event event = new Event();
        event.setName(req.getName());
        event.setCategory(Category.valueOf(req.getCategory().toUpperCase(Locale.ROOT)));
        event.setBasePrice(req.getBasePrice());
        event.setTotalSeats(req.getTotalSeats());
        event.setEventDate(date);
        event.setVenue(req.getVenue());

        // flush so a concurrent duplicate fails here and is mapped to 409 by the handler
        return EventResponse.from(repository.saveAndFlush(event));
    }

    @Transactional(readOnly = true)
    public EventResponse getById(long id) {
        return repository.findById(id)
                .map(EventResponse::from)
                .orElseThrow(() -> new EventNotFoundException(id));
    }
}