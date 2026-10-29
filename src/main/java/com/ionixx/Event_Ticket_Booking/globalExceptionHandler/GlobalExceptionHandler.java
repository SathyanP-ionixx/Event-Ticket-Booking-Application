package com.ionixx.Event_Ticket_Booking.globalExceptionHandler;

import com.ionixx.Event_Ticket_Booking.dto.ErrorResponse;
import com.ionixx.Event_Ticket_Booking.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps every failure to a consistent JSON body. Handlers do not log: they hand the error text (and
 * the exception for 5xx) to RequestIdFilter, which writes the request's single log line.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final String DUPLICATE_MESSAGE = "Event with same name and date is not allowed";
  private static final String UNIQUE_VIOLATION_SQL_STATE = "23505"; // PostgreSQL, H2, ...
  private static final String SEAT_INDEX = "uq_booking_seats_confirmed";

  // ---- 400 VALIDATION_FAILED (events: bean validation) ----
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleBeanValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    List<ErrorResponse.FieldDetail> details =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> new ErrorResponse.FieldDetail(toSnake(e.getField()), e.getDefaultMessage()))
            .sorted(Comparator.comparing(ErrorResponse.FieldDetail::field))
            .toList();
    RequestIdFilter.recordError(request, "Validation failed: " + describe(details));
    return validationFailed(details);
  }

  @ExceptionHandler(NoEventsFoundException.class)
  public ResponseEntity<ErrorResponse> handleNoEventFound(
      NoEventsFoundException ex, HttpServletRequest request) {
    RequestIdFilter.recordError(request, ex.getMessage());
    return notFound(ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {

    ErrorResponse response =
        ErrorResponse.of("INVALID_REQUEST", "Booking ID must be a positive integer");

    return ResponseEntity.badRequest().body(response);
  }

  // ---- 400 VALIDATION_FAILED (bookings: BookingValidator / seat range) ----
  @ExceptionHandler(BookingValidationException.class)
  public ResponseEntity<ErrorResponse> handleBookingValidation(
      BookingValidationException ex, HttpServletRequest request) {
    RequestIdFilter.recordError(request, "Validation failed: " + describe(ex.getDetails()));
    return validationFailed(ex.getDetails());
  }

  // ---- 400 INVALID_REQUEST (bad path id) ----
  @ExceptionHandler(InvalidRequestException.class)
  public ResponseEntity<ErrorResponse> handleInvalidRequest(
      InvalidRequestException ex, HttpServletRequest request) {
    String message = "ID must be a positive number";
    RequestIdFilter.recordError(request, message);
    return ResponseEntity.badRequest().body(ErrorResponse.of("INVALID_REQUEST", message));
  }

  // ---- 400 INVALID_REQUEST (malformed / empty JSON, wrong JSON types) ----
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadable(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    String message = "Malformed or unreadable request body";
    if (ex.getMessage() != null
        && ex.getMessage().toLowerCase().contains("required request body")) {
      message = "Request body is required";
    }
    RequestIdFilter.recordError(request, message);
    return ResponseEntity.badRequest().body(ErrorResponse.of("INVALID_REQUEST", message));
  }

  // ---- 401 INVALID_CREDENTIALS (login) ----
  @ExceptionHandler(InvalidCredentialsException.class)
  public ResponseEntity<ErrorResponse> handleInvalidCredentials(
      InvalidCredentialsException ex, HttpServletRequest request) {
    RequestIdFilter.recordError(request, ex.getMessage());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(ErrorResponse.of("INVALID_CREDENTIALS", ex.getMessage()));
  }

  // ---- 401 UNAUTHORIZED / 403 FORBIDDEN (safety net: the security chain normally answers these
  // itself;
  //      without these two, the catch-all below would turn them into a 500) ----
  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ErrorResponse> handleAuthentication(
      AuthenticationException ex, HttpServletRequest request) {
    RequestIdFilter.recordError(request, "Authentication is required");
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(ErrorResponse.of("UNAUTHORIZED", "Authentication is required"));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest request) {
    String message = "You do not have permission to perform this action";
    RequestIdFilter.recordError(request, message);
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("FORBIDDEN", message));
  }

  // ---- 409 USER_ALREADY_EXISTS (register) ----
  @ExceptionHandler(UserAlreadyExistsException.class)
  public ResponseEntity<ErrorResponse> handleUserExists(
      UserAlreadyExistsException ex, HttpServletRequest request) {
    RequestIdFilter.recordError(request, ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ErrorResponse.of("USER_ALREADY_EXISTS", ex.getMessage()));
  }

  // ---- 404 NOT_FOUND ----
  @ExceptionHandler(EventNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleEventNotFound(
      EventNotFoundException ex, HttpServletRequest request) {
    RequestIdFilter.recordError(request, ex.getMessage());
    return notFound(ex.getMessage());
  }

  @ExceptionHandler(BookingNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleBookingNotFound(
      BookingNotFoundException ex, HttpServletRequest request) {
    RequestIdFilter.recordError(request, ex.getMessage());
    return notFound(ex.getMessage());
  }

  // ---- 409 DUPLICATE_EVENT ----
  @ExceptionHandler(DuplicateEventException.class)
  public ResponseEntity<ErrorResponse> handleDuplicate(
      DuplicateEventException ex, HttpServletRequest request) {
    if (ex.getExistingEventId() != null) {
      MDC.put(RequestIdFilter.MDC_EVENT_ID, String.valueOf(ex.getExistingEventId()));
    }
    RequestIdFilter.recordError(request, DUPLICATE_MESSAGE);
    return duplicateResponse();
  }

  /**
   * Everything else: framework exceptions (404 route, 405, 415, ...) keep their own status,
   * anything unexpected becomes a generic 500 INTERNAL_ERROR and is logged at ERROR with its stack
   * trace.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleAny(Exception ex, HttpServletRequest request) {
    if (ex instanceof org.springframework.web.ErrorResponse springError) {
      HttpStatusCode status = springError.getStatusCode();
      ProblemDetail problem = springError.getBody();
      String message =
          problem.getDetail() != null ? problem.getDetail() : String.valueOf(ex.getMessage());
      HttpStatus resolved = HttpStatus.resolve(status.value());
      String code = resolved != null ? resolved.name() : "ERROR";

      RequestIdFilter.recordError(request, message, status.is5xxServerError() ? ex : null);
      return ResponseEntity.status(status)
          .headers(springError.getHeaders())
          .body(ErrorResponse.of(code, message));
    }
    return internalError(ex, request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponse> handleIntegrity(
      DataIntegrityViolationException ex, HttpServletRequest request) {
    if (isUniqueViolation(ex)) {
      String cause = String.valueOf(NestedExceptionUtils.getMostSpecificCause(ex).getMessage());
      if (cause.contains(SEAT_INDEX)) {
        String message = "One or more seats were just booked by someone else";
        RequestIdFilter.recordError(request, message);
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of("SEAT_ALREADY_BOOKED", message));
      }
      RequestIdFilter.recordError(request, DUPLICATE_MESSAGE);
      return duplicateResponse();
    }
    return internalError(ex, request);
  }

  // ---- helpers ----

  private ResponseEntity<ErrorResponse> internalError(Exception ex, HttpServletRequest request) {
    RequestIdFilter.recordError(
        request,
        ex.getClass().getSimpleName() + (ex.getMessage() != null ? ": " + ex.getMessage() : ""),
        ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred"));
  }

  private ResponseEntity<ErrorResponse> notFound(String message) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("NOT_FOUND", message));
  }

  private ResponseEntity<ErrorResponse> duplicateResponse() {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ErrorResponse.of("DUPLICATE_EVENT", DUPLICATE_MESSAGE));
  }

  private ResponseEntity<ErrorResponse> validationFailed(List<ErrorResponse.FieldDetail> details) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse("VALIDATION_FAILED", "Request validation failed", details));
  }

  private String describe(List<ErrorResponse.FieldDetail> details) {
    return details.stream()
        .map(d -> d.field() + " " + d.message())
        .collect(Collectors.joining("; "));
  }

  private boolean isUniqueViolation(Throwable throwable) {
    for (Throwable t = throwable; t != null; t = (t.getCause() == t ? null : t.getCause())) {
      if (t instanceof SQLException sql && UNIQUE_VIOLATION_SQL_STATE.equals(sql.getSQLState())) {
        return true;
      }
    }
    return false;
  }

  private String toSnake(String camel) {
    return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
  }
}
