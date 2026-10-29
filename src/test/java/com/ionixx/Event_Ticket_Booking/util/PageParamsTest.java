package com.ionixx.Event_Ticket_Booking.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.ionixx.Event_Ticket_Booking.dto.ErrorResponse;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.BookingValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class PageParamsTest {

  private static List<ErrorResponse.FieldDetail> errorsFor(String page, String size) {
    Throwable thrown = catchThrowable(() -> PageParams.parse(page, size));
    assertThat(thrown)
        .as("expected a validation error")
        .isInstanceOf(BookingValidationException.class);
    return ((BookingValidationException) thrown).getDetails();
  }

  @Test
  void absentParameters_useTheDefaults() {
    assertThat(PageParams.parse(null, null)).isEqualTo(new PageParams(0, 20));
  }

  @Test
  void blankParameters_useTheDefaults() {
    assertThat(PageParams.parse("", "  ")).isEqualTo(new PageParams(0, 20));
  }

  @Test
  void validValues_areParsed_andWhitespaceIsIgnored() {
    assertThat(PageParams.parse("3", "50")).isEqualTo(new PageParams(3, 50));
    assertThat(PageParams.parse(" 2 ", " 10 ")).isEqualTo(new PageParams(2, 10));
  }

  @Test
  void sizeBoundaries_areInclusive() {
    assertThat(PageParams.parse("0", "1").size()).isEqualTo(1);
    assertThat(PageParams.parse("0", "100").size()).isEqualTo(100);
  }

  @Test
  void badPage_isReported() {
    for (String bad : List.of("-1", "abc", "1.5", "99999999999")) {
      assertThat(errorsFor(bad, null))
          .as(bad)
          .containsExactly(new ErrorResponse.FieldDetail("page", "must be an integer >= 0"));
    }
  }

  @Test
  void badSize_isReported() {
    for (String bad : List.of("0", "-5", "101", "x")) {
      assertThat(errorsFor(null, bad))
          .as(bad)
          .containsExactly(
              new ErrorResponse.FieldDetail("size", "must be an integer between 1 and 100"));
    }
  }

  @Test
  void bothBad_areReportedTogether_pageFirst() {
    assertThat(errorsFor("-1", "500"))
        .extracting(ErrorResponse.FieldDetail::field)
        .containsExactly("page", "size");
  }

  @Test
  void anOffsetThatWouldOverflowAnInt_isRejected() {
    assertThat(errorsFor(String.valueOf(Integer.MAX_VALUE), "2"))
        .containsExactly(new ErrorResponse.FieldDetail("page", "is too large"));
  }

  @Test
  void toPageable_carriesPageAndSize_withoutASort() {
    Pageable pageable = new PageParams(2, 15).toPageable();

    assertThat(pageable.getPageNumber()).isEqualTo(2);
    assertThat(pageable.getPageSize()).isEqualTo(15);
    assertThat(pageable.getSort().isUnsorted()).isTrue();
  }
}
