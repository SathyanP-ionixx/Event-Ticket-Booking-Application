package com.ionixx.Event_Ticket_Booking.globalExceptionHandler;

/** Maps to HTTP 409. An event whose date has passed can no longer be edited. */
public class EventAlreadyOverException extends RuntimeException {
  public EventAlreadyOverException() {
    super("Event is already over and can no longer be edited");
  }
}
