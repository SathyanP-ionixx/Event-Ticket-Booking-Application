package com.ionixx.Event_Ticket_Booking.globalExceptionHandler;

public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException() {
        super("Invalid_request");
    }
}