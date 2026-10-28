package com.ionixx.Event_Ticket_Booking.validators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.ionixx.Event_Ticket_Booking.dto.BookingRequest;
import com.ionixx.Event_Ticket_Booking.dto.ErrorResponse;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.BookingValidationException;
import com.ionixx.Event_Ticket_Booking.support.TestData;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BookingValidatorTest {

  private final BookingValidator validator = new BookingValidator();

  private static BookingRequest valid() {
    return TestData.bookingRequest(1L, "John Doe", "john@example.com", 1, 2);
  }

  private Map<String, String> errorsFor(BookingRequest req) {
    Throwable thrown = catchThrowable(() -> validator.validate(req));
    assertThat(thrown).as("validation should fail").isInstanceOf(BookingValidationException.class);
    BookingValidationException ex = (BookingValidationException) thrown;
    Map<String, String> map = new LinkedHashMap<>();
    for (ErrorResponse.FieldDetail d : ex.getDetails()) {
      map.put(d.field(), d.message());
    }
    return map;
  }

  @Test
  void validRequest_passes() {
    assertThatCode(() -> validator.validate(valid())).doesNotThrowAnyException();
  }

  @Test
  void nullBody_isRejected() {
    assertThat(errorsFor(null)).containsEntry("body", "Request body is required");
  }

  @Test
  void eventId_requiredAndPositive() {
    BookingRequest r = valid();
    r.setEventId(null);
    assertThat(errorsFor(r)).containsEntry("event_id", "is required");

    r.setEventId(0L);
    assertThat(errorsFor(r)).containsEntry("event_id", "must be a positive integer");
  }

  @Test
  void customerName_rules() {
    BookingRequest r = valid();
    r.setCustomerName(null);
    assertThat(errorsFor(r)).containsEntry("customer_name", "is required");

    for (String bad : List.of("Jo", "John 3", "   ", "John_Doe", "A".repeat(51))) {
      r.setCustomerName(bad);
      assertThat(errorsFor(r))
          .as(bad)
          .containsEntry("customer_name", "must be 3-50 characters, letters and spaces only");
    }
  }

  @Test
  void customerEmail_rules() {
    BookingRequest r = valid();
    r.setCustomerEmail(null);
    assertThat(errorsFor(r)).containsEntry("customer_email", "is required");

    r.setCustomerEmail("not-an-email");
    assertThat(errorsFor(r)).containsEntry("customer_email", "must be a valid email");

    r.setCustomerEmail("a".repeat(250) + "@b.com"); // longer than 255
    assertThat(errorsFor(r)).containsEntry("customer_email", "must be a valid email");
  }

  @Test
  void seatNumbers_rules() {
    BookingRequest r = valid();
    r.setSeatNumbers(null);
    assertThat(errorsFor(r)).containsEntry("seat_numbers", "is required");

    r.setSeatNumbers(List.of());
    assertThat(errorsFor(r)).containsEntry("seat_numbers", "must contain 1 to 6 seat numbers");

    r.setSeatNumbers(List.of(1, 2, 3, 4, 5, 6, 7));
    assertThat(errorsFor(r)).containsEntry("seat_numbers", "must contain 1 to 6 seat numbers");

    r.setSeatNumbers(Arrays.asList(1, null));
    assertThat(errorsFor(r)).containsEntry("seat_numbers", "must be positive integers");

    r.setSeatNumbers(List.of(1, 0));
    assertThat(errorsFor(r)).containsEntry("seat_numbers", "must be positive integers");

    r.setSeatNumbers(List.of(3, 3));
    assertThat(errorsFor(r)).containsEntry("seat_numbers", "must not contain duplicates");
  }

  @Test
  void sixSeats_isTheAllowedMaximum() {
    BookingRequest r = valid();
    r.setSeatNumbers(List.of(1, 2, 3, 4, 5, 6));
    assertThatCode(() -> validator.validate(r)).doesNotThrowAnyException();
  }

  @Test
  void allErrorsAreReported_sortedByField() {
    Map<String, String> errors = errorsFor(new BookingRequest());
    assertThat(errors.keySet())
        .containsExactly("customer_email", "customer_name", "event_id", "seat_numbers");
  }
}
