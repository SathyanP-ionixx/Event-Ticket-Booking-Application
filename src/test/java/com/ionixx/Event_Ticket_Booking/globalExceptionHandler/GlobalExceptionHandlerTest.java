package com.ionixx.Event_Ticket_Booking.globalExceptionHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ionixx.Event_Ticket_Booking.dto.ErrorResponse;
import com.ionixx.Event_Ticket_Booking.filter.RequestIdFilter;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
  private final MockHttpServletRequest request = new MockHttpServletRequest();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  private static void assertError(
      ResponseEntity<ErrorResponse> res, HttpStatus status, String code, String message) {
    assertThat(res.getStatusCode()).isEqualTo(status);
    assertThat(res.getBody()).isNotNull();
    assertThat(res.getBody().error()).isEqualTo(code);
    if (message != null) {
      assertThat(res.getBody().message()).isEqualTo(message);
    }
  }

  @Test
  void typeMismatch_is400() {
    assertError(
        handler.handleTypeMismatch(mock(MethodArgumentTypeMismatchException.class)),
        HttpStatus.BAD_REQUEST,
        "INVALID_REQUEST",
        "Booking ID must be a positive integer");
  }

  @Test
  void bookingValidation_is400WithDetails() {
    var res =
        handler.handleBookingValidation(
            new BookingValidationException("seat_numbers", "is required"), request);

    assertError(res, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed");
    assertThat(res.getBody().details())
        .containsExactly(new ErrorResponse.FieldDetail("seat_numbers", "is required"));
  }

  @Test
  void invalidRequest_is400() {
    assertError(
        handler.handleInvalidRequest(new InvalidRequestException(), request),
        HttpStatus.BAD_REQUEST,
        "INVALID_REQUEST",
        "ID must be a positive number");
  }

  @Test
  void invalidCredentials_is401() {
    assertError(
        handler.handleInvalidCredentials(new InvalidCredentialsException(), request),
        HttpStatus.UNAUTHORIZED,
        "INVALID_CREDENTIALS",
        "Invalid email or password");
  }

  @Test
  void authenticationException_is401() {
    assertError(
        handler.handleAuthentication(new BadCredentialsException("x"), request),
        HttpStatus.UNAUTHORIZED,
        "UNAUTHORIZED",
        "Authentication is required");
  }

  @Test
  void accessDenied_is403_notA500() {
    assertError(
        handler.handleAccessDenied(new AccessDeniedException("x"), request),
        HttpStatus.FORBIDDEN,
        "FORBIDDEN",
        "You do not have permission to perform this action");
  }

  @Test
  void userAlreadyExists_is409() {
    assertError(
        handler.handleUserExists(new UserAlreadyExistsException(), request),
        HttpStatus.CONFLICT,
        "USER_ALREADY_EXISTS",
        "A user with this email already exists");
  }

  @Test
  void notFound_is404() {
    assertError(
        handler.handleEventNotFound(new EventNotFoundException(5), request),
        HttpStatus.NOT_FOUND,
        "NOT_FOUND",
        "Event with id 5 not found");
    assertError(
        handler.handleBookingNotFound(
            new BookingNotFoundException("Booking not found with id 9"), request),
        HttpStatus.NOT_FOUND,
        "NOT_FOUND",
        "Booking not found with id 9");
  }

  @Test
  void duplicateEvent_is409_andTagsTheExistingEventInTheMdc() {
    assertError(
        handler.handleDuplicate(new DuplicateEventException(12L), request),
        HttpStatus.CONFLICT,
        "DUPLICATE_EVENT",
        "Event with same name and date is not allowed");
    assertThat(MDC.get(RequestIdFilter.MDC_EVENT_ID)).isEqualTo("12");
  }

  @Test
  void duplicateEvent_withUnknownId_leavesTheMdcAlone() {
    assertError(
        handler.handleDuplicate(new DuplicateEventException(null), request),
        HttpStatus.CONFLICT,
        "DUPLICATE_EVENT",
        null);
    assertThat(MDC.get(RequestIdFilter.MDC_EVENT_ID)).isNull();
  }

  @Test
  void otherIntegrityViolation_is500_withoutLeakingDetails() {
    var ex = new DataIntegrityViolationException("fk", new SQLException("fk violation", "23503"));

    assertError(
        handler.handleIntegrity(ex, request),
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "An unexpected error occurred");
  }

  @Test
  void frameworkException_keepsItsOwnStatusAndMessage() {
    var res =
        handler.handleAny(
            new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "nope"), request);

    assertError(res, HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "nope");
  }

  @Test
  void frameworkException_withoutReason_fallsBackToTheExceptionMessage() {
    var res = handler.handleAny(new ResponseStatusException(HttpStatus.NOT_FOUND), request);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(res.getBody().error()).isEqualTo("NOT_FOUND");
    assertThat(res.getBody().message()).isNotBlank();
  }

  @Test
  void framework5xx_isStillMappedToItsStatus() {
    var res =
        handler.handleAny(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "upstream"), request);

    assertError(res, HttpStatus.BAD_GATEWAY, "BAD_GATEWAY", "upstream");
  }

  @Test
  void anythingUnexpected_is500_withAGenericMessage() {
    assertError(
        handler.handleAny(new RuntimeException("secret internals"), request),
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "An unexpected error occurred");
    assertError(
        handler.handleAny(new RuntimeException(), request),
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "An unexpected error occurred");
  }

  @Test
  void validationDetailsList_isImmutableCopy() {
    var ex = new BookingValidationException(List.of(new ErrorResponse.FieldDetail("a", "b")));

    assertThat(ex.getMessage()).isEqualTo("Validation failed");
    assertThat(ex.getDetails()).hasSize(1);
  }
}
