package com.ionixx.Event_Ticket_Booking.util;

import com.ionixx.Event_Ticket_Booking.dto.ErrorResponse;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.BookingValidationException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Validated paging parameters. Parameters arrive as raw strings (like path ids, see IDparser) so a
 * bad value becomes the project's 400 VALIDATION_FAILED instead of a generic type-mismatch error.
 * There is deliberately no client-controlled sort: the order is fixed by the repository query.
 */
public record PageParams(int page, int size) {

  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  public static PageParams parse(String rawPage, String rawSize) {
    List<ErrorResponse.FieldDetail> errors = new ArrayList<>();

    Integer page = parseInt(rawPage, 0, 0, Integer.MAX_VALUE);
    Integer size = parseInt(rawSize, DEFAULT_SIZE, 1, MAX_SIZE);

    if (page == null) {
      errors.add(new ErrorResponse.FieldDetail("page", "must be an integer >= 0"));
    }
    if (size == null) {
      errors.add(
          new ErrorResponse.FieldDetail("size", "must be an integer between 1 and " + MAX_SIZE));
    }
    // Spring Data turns offset (page * size) into an int; refuse what would overflow it
    if (errors.isEmpty() && (long) page * size > Integer.MAX_VALUE) {
      errors.add(new ErrorResponse.FieldDetail("page", "is too large"));
    }
    if (!errors.isEmpty()) {
      throw new BookingValidationException(errors);
    }
    return new PageParams(page, size);
  }

  public Pageable toPageable() {
    return PageRequest.of(page, size); // unsorted: the repository method name fixes the order
  }

  /**
   * @return the parsed value, the default when absent or blank, or null when invalid / out of
   *     range.
   */
  private static Integer parseInt(String raw, int defaultValue, int min, int max) {
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    try {
      int value = Integer.parseInt(raw.strip());
      return (value >= min && value <= max) ? value : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
