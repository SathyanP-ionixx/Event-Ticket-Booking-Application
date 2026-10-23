package com.ionixx.Event_Ticket_Booking.controller;

import com.ionixx.Event_Ticket_Booking.dto.BookingRequest;
import com.ionixx.Event_Ticket_Booking.dto.BookingResponse;
import com.ionixx.Event_Ticket_Booking.filter.RequestIdFilter;
import com.ionixx.Event_Ticket_Booking.service.BookingService;
import com.ionixx.Event_Ticket_Booking.util.IDparser;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    // API 3 - Book tickets
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BookingResponse> createBooking(@RequestBody BookingRequest request) {
        // the event the client asked for, set even if validation or the lookup fails
        if (request != null && request.getEventId() != null) {
            MDC.put(RequestIdFilter.MDC_EVENT_ID, String.valueOf(request.getEventId()));
        }
        BookingResponse created = bookingService.createBooking(request);
        tag(created);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // API 4 - id read as String so a bad value gives our own 400 INVALID_REQUEST
    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable("id") String rawId) {
        long id = IDparser.parsePositiveId(rawId);
        // the requested id, set even if it turns out to be a 404
        MDC.put(RequestIdFilter.MDC_BOOKING_ID, String.valueOf(id));
        BookingResponse found = bookingService.getBooking(id);
        tag(found);
        return ResponseEntity.ok(found);
    }

    /** Adds booking id, event id and CONFIRMED/REJECTED to the request's single log line. */
    private void tag(BookingResponse b) {
        MDC.put(RequestIdFilter.MDC_BOOKING_ID, String.valueOf(b.getId()));
        MDC.put(RequestIdFilter.MDC_EVENT_ID, String.valueOf(b.getEventId()));
        MDC.put(RequestIdFilter.MDC_BOOKING_STATUS, b.getStatus());
    }
}