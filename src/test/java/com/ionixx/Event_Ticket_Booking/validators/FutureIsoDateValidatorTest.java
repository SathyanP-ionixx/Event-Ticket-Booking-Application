package com.ionixx.Event_Ticket_Booking.validators;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class FutureIsoDateValidatorTest {

    private final FutureIsoDateValidator validator = new FutureIsoDateValidator();
    private ConstraintValidatorContext ctx;

    @BeforeEach
    void setUp() {
        ctx = mock(ConstraintValidatorContext.class, RETURNS_DEEP_STUBS);
    }

    @Test
    void null_isValid_becauseNotNullReportsIt() {
        assertTrue(validator.isValid(null, ctx));
        verifyNoInteractions(ctx);
    }

    @Test
    void tomorrow_isValid() {
        assertTrue(validator.isValid(LocalDate.now().plusDays(1).toString(), ctx));
    }

    @Test
    void farFuture_isValid() {
        assertTrue(validator.isValid("2099-12-31", ctx));
    }

    @Test
    void today_isRejected_asNotFuture() {
        assertFalse(validator.isValid(LocalDate.now().toString(), ctx));
        verify(ctx).buildConstraintViolationWithTemplate("must be a future date");
    }

    @Test
    void yesterday_isRejected_asNotFuture() {
        assertFalse(validator.isValid(LocalDate.now().minusDays(1).toString(), ctx));
        verify(ctx).buildConstraintViolationWithTemplate("must be a future date");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "20-12-2099", "2099/12/20", "2099-1-5", "2099-13-01", "2099-02-30", "tomorrow", "2099-12-20T10:00"})
    void badFormat_isRejected(String value) {
        assertFalse(validator.isValid(value, ctx));
        verify(ctx).buildConstraintViolationWithTemplate("must be in format yyyy-mm-dd");
    }
}
