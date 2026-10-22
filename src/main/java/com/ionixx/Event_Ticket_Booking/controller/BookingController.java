package com.ionixx.Event_Ticket_Booking.controller;

import com.ionixx.Event_Ticket_Booking.dto.BookingRequest;
import com.ionixx.Event_Ticket_Booking.dto.BookingResponse;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.InvalidRequestException;
import com.ionixx.Event_Ticket_Booking.service.BookingService;
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
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.createBooking(request));
    }

    // API 4 - Get a booking (non-numeric id -> 400 via exception handler)
    @GetMapping("/{id}")
    public BookingResponse getBooking(@PathVariable String id) {

        long bookingId;

        try {
            bookingId = Long.parseLong(id);
        } catch (NumberFormatException ex) {
            throw new InvalidRequestException();
        }

        if (bookingId <= 0) {
            throw new InvalidRequestException();
        }

        return bookingService.getBooking(bookingId);
    }

}
