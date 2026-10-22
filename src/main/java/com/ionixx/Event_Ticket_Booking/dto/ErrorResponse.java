package com.ionixx.Event_Ticket_Booking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String error, String message, List<FieldDetail> details) {

    public record FieldDetail(String field, String message) {}

    public static ErrorResponse of(String error) {
        return new ErrorResponse(error, null, null);
    }

    public static ErrorResponse of(String error, String message) {
        return new ErrorResponse(error, message, null);
    }

    public static ErrorResponse validation(List<FieldDetail> details) {
        return new ErrorResponse("VALIDATION_FAILED", null, details);
    }
}

