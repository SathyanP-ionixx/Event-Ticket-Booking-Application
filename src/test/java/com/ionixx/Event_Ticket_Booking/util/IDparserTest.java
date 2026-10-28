package com.ionixx.Event_Ticket_Booking.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IDparserTest {

  @Test
  void positiveNumber_isParsed() {
    assertThat(IDparser.parsePositiveId("42")).isEqualTo(42L);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-5", "abc", "", " 7", "1.5", "99999999999999999999"})
  void everythingElse_isRejected(String raw) {
    assertThatThrownBy(() -> IDparser.parsePositiveId(raw))
        .isInstanceOf(InvalidRequestException.class);
  }
}
