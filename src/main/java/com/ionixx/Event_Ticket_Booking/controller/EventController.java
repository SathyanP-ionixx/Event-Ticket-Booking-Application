package com.ionixx.Event_Ticket_Booking.controller;

import com.ionixx.Event_Ticket_Booking.dto.EventRequest;
import com.ionixx.Event_Ticket_Booking.dto.EventResponse;
import com.ionixx.Event_Ticket_Booking.service.EventService;
import com.ionixx.Event_Ticket_Booking.filter.RequestIdFilter;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.InvalidRequestException;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse create(@Valid @RequestBody EventRequest request) {
        EventResponse created = service.create(request);
        // top-level "eventId" on the request's single log line
        MDC.put(RequestIdFilter.MDC_EVENT_ID, String.valueOf(created.id()));
        return created;
    }

    // id is read as a String so a bad value gives our own 400 instead of Spring's default
    @GetMapping("/{id}")
    public EventResponse getById(@PathVariable("id") String rawId) {
        long id = parsePositiveId(rawId);
        // the requested id, set even if it turns out to be a 404
        MDC.put(RequestIdFilter.MDC_EVENT_ID, String.valueOf(id));
        return service.getById(id);
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