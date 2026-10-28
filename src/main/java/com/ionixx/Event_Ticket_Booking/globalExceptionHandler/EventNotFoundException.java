package com.ionixx.Event_Ticket_Booking.globalExceptionHandler;

public class EventNotFoundException extends RuntimeException {
  public EventNotFoundException(long id) {
    super("Event with id " + id + " not found");
  }
}
