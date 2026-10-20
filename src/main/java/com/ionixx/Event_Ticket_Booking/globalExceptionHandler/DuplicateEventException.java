package com.ionixx.Event_Ticket_Booking.globalExceptionHandler;

public class DuplicateEventException extends RuntimeException {
    public DuplicateEventException() {
        super("DUPLICATE_EVENT");
    }
}
