package com.ionixx.Event_Ticket_Booking.validators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.authorization.ConditionalAuthorizationManager.when;

import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FutureIsoDateValidatorTest {

  private final FutureIsoDateValidator validator = new FutureIsoDateValidator();

  private final ConstraintValidatorContext ctx = mock(ConstraintValidatorContext.class);
  private final ConstraintValidatorContext.ConstraintViolationBuilder builder =
      mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);

  FutureIsoDateValidatorTest() {
    when(ctx.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
  }

  @Test
  void null_isLeftToNotNull() {
    assertThat(validator.isValid(null, ctx)).isTrue();
  }

  @Test
  void futureDate_isValid() {
    assertThat(validator.isValid(LocalDate.now().plusDays(1).toString(), ctx)).isTrue();
  }

  @Test
  void today_isNotValid() {
    assertThat(validator.isValid(LocalDate.now().toString(), ctx)).isFalse();
    verify(ctx).buildConstraintViolationWithTemplate("must be a future date");
  }

  @Test
  void pastDate_isNotValid() {
    assertThat(validator.isValid("2000-01-01", ctx)).isFalse();
    verify(ctx).disableDefaultConstraintViolation();
    verify(builder).addConstraintViolation();
  }

  @Test
  void wrongFormat_isNotValid() {
    assertThat(validator.isValid("01-01-2099", ctx)).isFalse();
    verify(ctx).buildConstraintViolationWithTemplate("must be in format yyyy-mm-dd");
  }

  @Test
  void impossibleDate_isNotValid() {
    assertThat(validator.isValid("2099-02-30", ctx)).isFalse();
  }
}
