package com.ionixx.Event_Ticket_Booking.validators;

import com.ionixx.Event_Ticket_Booking.dto.BookingRequest;
import com.ionixx.Event_Ticket_Booking.dto.ErrorResponse;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.BookingValidationException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class BookingValidator {

  private static final Pattern NAME = Pattern.compile("^[A-Za-z ]{3,50}$");
  private static final Pattern EMAIL =
      Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

  public void validate(BookingRequest req) {
    if (req == null) {
      throw new BookingValidationException("body", "Request body is required");
    }
    Map<String, String> errors = new TreeMap<>();

    if (req.getEventId() == null) {
      errors.put("event_id", "is required");
    } else if (req.getEventId() <= 0) {
      errors.put("event_id", "must be a positive integer");
    }

    String name = req.getCustomerName();
    if (name == null) {
      errors.put("customer_name", "is required");
    } else if (name.isBlank() || !NAME.matcher(name).matches()) {
      errors.put("customer_name", "must be 3-50 characters, letters and spaces only");
    }

    String email = req.getCustomerEmail();
    if (email == null) {
      errors.put("customer_email", "is required");
    } else if (email.length() > 255 || !EMAIL.matcher(email).matches()) {
      errors.put("customer_email", "must be a valid email");
    }

    List<Integer> seats = req.getSeatNumbers();
    if (seats == null) {
      errors.put("seat_numbers", "is required");
    } else if (seats.isEmpty() || seats.size() > 6) {
      errors.put("seat_numbers", "must contain 1 to 6 seat numbers");
    } else if (seats.stream().anyMatch(s -> s == null || s <= 0)) {
      errors.put("seat_numbers", "must be positive integers");
    } else if (new HashSet<>(seats).size() != seats.size()) {
      errors.put("seat_numbers", "must not contain duplicates");
    }

    if (!errors.isEmpty()) {
      throw new BookingValidationException(
          errors.entrySet().stream()
              .map(e -> new ErrorResponse.FieldDetail(e.getKey(), e.getValue()))
              .toList());
    }
  }
}
