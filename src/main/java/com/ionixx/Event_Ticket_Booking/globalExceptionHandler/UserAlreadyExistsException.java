package com.ionixx.Event_Ticket_Booking.globalExceptionHandler;

/** Maps to HTTP 409. */
public class UserAlreadyExistsException extends RuntimeException {
  public UserAlreadyExistsException() {
    super("A user with this email already exists");
  }
}
