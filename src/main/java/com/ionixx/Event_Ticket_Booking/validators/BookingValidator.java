package com.ionixx.Event_Ticket_Booking.validators;


import com.ionixx.Event_Ticket_Booking.dto.BookingRequest;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.BookingValidationException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class BookingValidator {

    private static final Pattern NAME = Pattern.compile("^[A-Za-z ]{3,50}$");
    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public void validate(BookingRequest req) {
        if (req == null) {
            throw new BookingValidationException("body", "Request body is required");
        }
        if (req.getEventId() == null || req.getEventId() <= 0) {
            throw new BookingValidationException("event_id", "event_id must be a positive integer");
        }

        String name = req.getCustomerName();
        if (name == null || !NAME.matcher(name).matches()) {
            throw new BookingValidationException("customer_name",
                    "customer_name must be 3-50 characters, letters and spaces only");
        }

        String email = req.getCustomerEmail();
        if (email == null || email.length() > 255 || !EMAIL.matcher(email).matches()) {
            throw new BookingValidationException("customer_email", "customer_email must be a valid email");
        }

        List<Integer> seats = req.getSeatNumbers();
        if (seats == null || seats.isEmpty() || seats.size() > 6) {
            throw new BookingValidationException("seat_numbers", "seat_numbers must contain 1 to 6 seat numbers");
        }
        for (Integer s : seats) {
            if (s == null || s <= 0) {
                throw new BookingValidationException("seat_numbers", "seat_numbers must be positive integers");
            }
        }
        if (new HashSet<>(seats).size() != seats.size()) {
            throw new BookingValidationException("seat_numbers", "seat_numbers must not contain duplicates");
        }
    }
}
