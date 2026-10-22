package com.ionixx.Event_Ticket_Booking.globalExceptionHandler;

public class DuplicateEventException extends RuntimeException {

    private final Long existingEventId; // null when unknown (concurrent insert)

    public DuplicateEventException(Long existingEventId) {
        super("Event with same name and date is not allowed");
        this.existingEventId = existingEventId;
    }

    public Long getExistingEventId() {
        return existingEventId;
    }
}