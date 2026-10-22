package com.ionixx.Event_Ticket_Booking.globalExceptionHandler;

/** Maps to HTTP 400. If your event module already has an equivalent, use that instead. */
public class BookingValidationException extends RuntimeException {
    private final String field;

    public BookingValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() { return field; }
}
