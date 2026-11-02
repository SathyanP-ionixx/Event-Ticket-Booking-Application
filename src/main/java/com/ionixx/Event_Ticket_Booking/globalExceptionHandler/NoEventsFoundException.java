package com.ionixx.Event_Ticket_Booking.globalExceptionHandler;

public class NoEventsFoundException extends RuntimeException {
  public NoEventsFoundException(String message) {
    super(message);
  }
}
