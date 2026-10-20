package com.ionixx.Event_Ticket_Booking.event.controller;

import com.ionixx.Event_Ticket_Booking.event.dto.EventRequest;
import com.ionixx.Event_Ticket_Booking.event.dto.EventResponse;
import com.ionixx.Event_Ticket_Booking.event.repository.EventRepository;
import com.ionixx.Event_Ticket_Booking.event.service.EventService;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.InvalidRequestException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService service;


    public EventController(EventService service) {

        this.service = service;
    }

    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse create(@Valid @RequestBody EventRequest request) {
        return service.create(request);
    }
    @GetMapping("/{id}")
    public EventResponse getById(@PathVariable("id") String rawId) {
        return service.getById(parsePositiveId(rawId));
    }

    private long parsePositiveId(String raw) {
        try {
            long id = Long.parseLong(raw);
            if (id <= 0) {
                throw new InvalidRequestException();
            }
            return id;
        } catch (NumberFormatException e) {
            throw new InvalidRequestException();
        }
    }


}
