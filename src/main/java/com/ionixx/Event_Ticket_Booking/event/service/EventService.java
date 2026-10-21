package com.ionixx.Event_Ticket_Booking.event.service;

import com.ionixx.Event_Ticket_Booking.event.dto.CreatedEvent;
import com.ionixx.Event_Ticket_Booking.event.dto.EventResponse;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.DuplicateEventException;
import com.ionixx.Event_Ticket_Booking.event.dto.EventRequest;
import com.ionixx.Event_Ticket_Booking.event.entity.Event;
import com.ionixx.Event_Ticket_Booking.event.enums.Category;
import com.ionixx.Event_Ticket_Booking.event.repository.EventRepository;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.EventNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Service
public class EventService {

    // explicit name so "logger_name" in the JSON log is exactly this value
    private static final Logger log =
            LoggerFactory.getLogger("com.ionixx.eventticketbooking.service.EventService");
    private final EventRepository repository;

    public EventService(EventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CreatedEvent create(EventRequest req) {
        String eventLogId = newEventLogId();
        LocalDate date = LocalDate.parse(req.getEventDate());
        String name = req.getName().trim();

        if (repository.existsByNameAndEventDate(name, date)) {
            log.warn("Duplicate event rejected eventDate={}", date);
            throw new DuplicateEventException();
        }

        Event event = new Event();
        event.setName(name);
        event.setCategory(Category.valueOf(req.getCategory().toUpperCase(Locale.ROOT)));
        event.setBasePrice(req.getBasePrice());
        event.setTotalSeats(req.getTotalSeats());
        event.setEventDate(date);
        event.setVenue(req.getVenue().trim());

        // flush so a race on the unique constraint surfaces here (-> 409 via handler)
        Event saved = repository.saveAndFlush(event);
        log.info("Event created successfully: eventLogId={}, eventId={}", eventLogId, saved.getId());
        return new CreatedEvent(EventResponse.from(saved), eventLogId);
    }

    @Transactional(readOnly = true)
    public EventResponse getById(long id) {
        log.debug("Fetching event id={}", id);
        return repository.findById(id)
                .map(EventResponse::from)
                .orElseThrow(() -> {
                    log.warn("Event not found id={}", id);
                    return new EventNotFoundException(id);

                });

    }

    private String newEventLogId() {
        String hex = UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        return ("ETB" + hex).substring(0, 32);
    }
}