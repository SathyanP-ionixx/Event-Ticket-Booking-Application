package com.ionixx.Event_Ticket_Booking.globalExceptionHandler;

/** Maps to HTTP 404. If your event module already has an equivalent, use that instead. */
public class BookingNotFoundException extends RuntimeException {
    public BookingNotFoundException(String message) {
        super(message);
    }
}
