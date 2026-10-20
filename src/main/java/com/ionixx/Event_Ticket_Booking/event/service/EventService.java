package com.ionixx.Event_Ticket_Booking.event.service;

import com.ionixx.Event_Ticket_Booking.event.dto.EventResponse;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.DuplicateEventException;
import com.ionixx.Event_Ticket_Booking.event.dto.EventRequest;
import com.ionixx.Event_Ticket_Booking.event.entity.Event;
import com.ionixx.Event_Ticket_Booking.event.enums.Category;
import com.ionixx.Event_Ticket_Booking.event.repository.EventRepository;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.EventNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Locale;

@Service
public class EventService {

    private final EventRepository repository;

    public EventService(EventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public EventResponse create(EventRequest req) {
        LocalDate date = LocalDate.parse(req.getEventDate());
        String name = req.getName().trim();

        if (repository.existsByNameAndEventDate(name, date)) {
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
        return EventResponse.from(saved);
    }
    @Transactional(readOnly = true)
    public EventResponse getById(long id) {
        return repository.findById(id)
                .map(EventResponse::from)
                .orElseThrow(() -> new EventNotFoundException(id));
    }
}
