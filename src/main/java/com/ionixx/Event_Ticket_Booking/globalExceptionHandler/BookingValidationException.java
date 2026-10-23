package com.ionixx.Event_Ticket_Booking.globalExceptionHandler;

import com.ionixx.Event_Ticket_Booking.dto.ErrorResponse;

import java.util.List;

/** Maps to HTTP 400. If your event module already has an equivalent, use that instead. */
public class BookingValidationException extends RuntimeException {
    private final List<ErrorResponse.FieldDetail> details;

    public BookingValidationException(String field, String message) {
        this(List.of(new ErrorResponse.FieldDetail(field, message)));
    }

    public BookingValidationException(List<ErrorResponse.FieldDetail> details) {
        super("Validation failed");
        this.details = List.copyOf(details);
    }

    public List<ErrorResponse.FieldDetail> getDetails() {
        return details;
    }
}
