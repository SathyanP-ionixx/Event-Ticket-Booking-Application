package com.ionixx.Event_Ticket_Booking.controller;

import com.ionixx.Event_Ticket_Booking.dto.BookingRequest;
import com.ionixx.Event_Ticket_Booking.dto.BookingResponse;
import com.ionixx.Event_Ticket_Booking.service.BookingService;
import org.springframework.http.HttpStatus;
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
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(@RequestBody BookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.createBooking(request));
    }

    // API 4 - Get a booking (non-numeric id -> 400 via exception handler)
    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable("id") Long id) {
        return ResponseEntity.ok(bookingService.getBooking(id));
    }
}
