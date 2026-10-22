package com.ionixx.Event_Ticket_Booking.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Request logging in one place.
 *
 * <ul>
 *   <li>Puts app, requestId, method and path into the MDC for the whole request.</li>
 *   <li>Returns the request id in the X-Request-Id response header.</li>
 *   <li>Writes exactly ONE log line per request, after it has finished:
 *       INFO for success, WARN for 4xx, ERROR (with stack trace) for 5xx.</li>
 * </ul>
 *
 * Other layers add context by putting {@link #MDC_EVENT_ID} in the MDC, or report the error text
 * with {@link #recordError}; this filter turns all of it into the single final line.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    // Fixed name so "logger_name" in the JSON log is stable. To use the real class name instead,
    // replace with LoggerFactory.getLogger(RequestIdFilter.class).
    private static final Logger log =
            LoggerFactory.getLogger("com.ionixx.eventticketbooking.filter.RequestIdFilter");

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_APP = "app";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_METHOD = "method";
    public static final String MDC_PATH = "path";
    public static final String MDC_EVENT_ID = "eventId";

    private static final String ERROR_MESSAGE_ATTR = RequestIdFilter.class.getName() + ".errorMessage";
    private static final String ERROR_CAUSE_ATTR = RequestIdFilter.class.getName() + ".errorCause";

    // only accept safe client-supplied ids (prevents log injection)
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final String appName;

    public RequestIdFilter(@Value("${app.name:event-ticket-booking}") String appName) {
        this.appName = appName;
    }

    /** Error text that becomes the "message" of this request's log line. */
    public static void recordError(HttpServletRequest request, String message) {
        recordError(request, message, null);
    }

    /** Same, plus the exception so a 5xx line carries its stack trace. */
    public static void recordError(HttpServletRequest request, String message, Throwable cause) {
        request.setAttribute(ERROR_MESSAGE_ATTR, message);
        if (cause != null) {
            request.setAttribute(ERROR_CAUSE_ATTR, cause);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // health / metrics probes would flood the log
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String requestId = resolveRequestId(request);

        MDC.put(MDC_APP, appName);
        MDC.put(MDC_REQUEST_ID, requestId);
        MDC.put(MDC_METHOD, request.getMethod());
        MDC.put(MDC_PATH, request.getRequestURI()); // no query string: may hold sensitive data

        response.setHeader(REQUEST_ID_HEADER, requestId);

        long start = System.nanoTime();
        Throwable escaped = null;
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException e) {
            escaped = (e instanceof ServletException && e.getCause() != null) ? e.getCause() : e;
            throw e;
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            try {
                writeSingleLogLine(request, response, escaped, durationMs);
            } finally {
                MDC.clear(); // threads are pooled, never leak context into the next request
            }
        }
    }

    private void writeSingleLogLine(HttpServletRequest request, HttpServletResponse response,
                                    Throwable escaped, long durationMs) {

        // an exception that escaped every handler: the container will answer 500 after we return,
        // so response.getStatus() would still say 200 here
        int status = escaped != null ? 500 : response.getStatus();

        Throwable cause = escaped;
        if (cause == null && request.getAttribute(ERROR_CAUSE_ATTR) instanceof Throwable t) {
            cause = t;
        }

        String message = null;
        if (request.getAttribute(ERROR_MESSAGE_ATTR) instanceof String s) {
            message = s;
        } else if (escaped != null) {
            message = escaped.getMessage() != null ? escaped.getMessage() : escaped.getClass().getSimpleName();
        } else if (status >= 400) {
            message = "Request failed with status " + status;
        }

        if (message == null) {
            log.info("HTTP request completed: status={}, durationMs={}", status, durationMs);
            return;
        }

        // error line: message = the error text; status and duration stay queryable as fields
        MDC.put("status", String.valueOf(status));
        MDC.put("durationMs", String.valueOf(durationMs));
        if (status >= 500) {
            log.error(message, cause); // null cause -> no stack_trace field
        } else {
            log.warn(message);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        if (incoming != null && SAFE_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return "REQ-" + UUID.randomUUID().toString().replace("-", "");
    }
}