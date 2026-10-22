package com.ionixx.Event_Ticket_Booking.globalExceptionHandler;

import com.ionixx.Event_Ticket_Booking.dto.ErrorResponse;
import com.ionixx.Event_Ticket_Booking.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps every failure to a consistent JSON body. Handlers do not log: they hand the error text
 * (and the exception for 5xx) to RequestIdFilter, which writes the request's single log line.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String DUPLICATE_MESSAGE = "Event with same name and date is not allowed";
    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505"; // PostgreSQL, H2, ...

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        List<ErrorResponse.FieldDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ErrorResponse.FieldDetail(toSnake(e.getField()), e.getDefaultMessage()))
                .sorted(Comparator.comparing(ErrorResponse.FieldDetail::field))
                .toList();
        RequestIdFilter.recordError(request, "Validation failed: " + details.stream()
                .map(d -> d.field() + " " + d.message())
                .collect(Collectors.joining("; ")));
        return validationFailed(details);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException ex,
                                                              HttpServletRequest request) {
        String message = "ID must be a positive number";
        RequestIdFilter.recordError(request, message);
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_REQUEST", message, List.of()));
    }

    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EventNotFoundException ex,
                                                        HttpServletRequest request) {
        RequestIdFilter.recordError(request, ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOT_FOUND", ex.getMessage()));
    }

    // wrong JSON types, e.g. "total_seats": "abc", or a missing / malformed body
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex,
                                                          HttpServletRequest request) {
        String message = "Malformed or unreadable request body";
        if (ex.getMessage() != null
                && ex.getMessage().toLowerCase().contains("required request body")) {
            message = "Request body is required";
        }
        RequestIdFilter.recordError(request, message);
        return validationFailed(List.of(new ErrorResponse.FieldDetail("body", message)));
    }

    @ExceptionHandler(DuplicateEventException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateEventException ex,
                                                         HttpServletRequest request) {
        if (ex.getExistingEventId() != null) {
            MDC.put(RequestIdFilter.MDC_EVENT_ID, String.valueOf(ex.getExistingEventId()));
        }
        RequestIdFilter.recordError(request, DUPLICATE_MESSAGE);
        return duplicateResponse();
    }

    // concurrent insert that slipped past the duplicate check, or any other DB constraint failure
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException ex,
                                                         HttpServletRequest request) {
        if (isUniqueViolation(ex)) {
            RequestIdFilter.recordError(request, DUPLICATE_MESSAGE);
            return duplicateResponse();
        }
        // not a duplicate: validation should have caught it, so this is a bug -> 500, never leak details
        return internalError(ex, request);
    }

    /**
     * Everything else: framework exceptions (404 route, 405, 415, ...) keep their own status,
     * anything unexpected becomes a generic 500 and is logged at ERROR with its stack trace.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAny(Exception ex, HttpServletRequest request) {
        if (ex instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatusCode status = springError.getStatusCode();
            ProblemDetail problem = springError.getBody();
            String message = problem.getDetail() != null ? problem.getDetail() : String.valueOf(ex.getMessage());
            HttpStatus resolved = HttpStatus.resolve(status.value());
            String code = resolved != null ? resolved.name() : "ERROR";

            RequestIdFilter.recordError(request, message, status.is5xxServerError() ? ex : null);
            return ResponseEntity.status(status)
                    .headers(springError.getHeaders())
                    .body(ErrorResponse.of(code, message));
        }
        return internalError(ex, request);
    }

    private ResponseEntity<ErrorResponse> internalError(Exception ex, HttpServletRequest request) {
        RequestIdFilter.recordError(request,
                ex.getClass().getSimpleName() + (ex.getMessage() != null ? ": " + ex.getMessage() : ""), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    private ResponseEntity<ErrorResponse> duplicateResponse() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(DUPLICATE_MESSAGE));
    }

    private ResponseEntity<ErrorResponse> validationFailed(List<ErrorResponse.FieldDetail> details) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_FAILED", "Request validation failed", details));
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

    @ExceptionHandler(BookingValidationException.class)
    public ResponseEntity<Map<String, String>> handleValidation(BookingValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("field", ex.getField(), "message", ex.getMessage()));
    }

    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(BookingNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("field", ex.getName(), "message", ex.getName() + " must be a number"));
    }

//    @ExceptionHandler(HttpMessageNotReadableException.class)
//    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException ex) {
//        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
//                .body(Map.of("field", "body", "message", "Malformed or invalid request body"));
//    }
}