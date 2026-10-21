package com.ionixx.Event_Ticket_Booking.event.controller;

import com.ionixx.Event_Ticket_Booking.event.dto.CreatedEvent;
import com.ionixx.Event_Ticket_Booking.event.dto.EventRequest;
import com.ionixx.Event_Ticket_Booking.event.dto.EventResponse;

import com.ionixx.Event_Ticket_Booking.event.service.EventService;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.InvalidRequestException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService service;
    // explicit name so "logger_name" in the JSON log is exactly this value
    private static final Logger log =
            LoggerFactory.getLogger("com.ionixx.eventticketbooking.controller.EventController");

    public EventController(EventService service) {

        this.service = service;
    }

    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse create(@Valid @RequestBody EventRequest request) {
        CreatedEvent created = service.create(request);
        log.info("Event creation response prepared: eventLogId={}, eventId={}",
                created.eventLogId(), created.response().id());
        return created.response();
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