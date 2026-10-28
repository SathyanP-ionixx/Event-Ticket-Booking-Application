package com.ionixx.Event_Ticket_Booking.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import com.ionixx.Event_Ticket_Booking.support.LogCapture;
import jakarta.servlet.ServletException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

  private final RequestIdFilter filter = new RequestIdFilter("test-app");
  private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events/1");
  private final MockHttpServletResponse response = new MockHttpServletResponse();
  private LogCapture logs;

  @BeforeEach
  void attach() {
    logs = LogCapture.attach(LogCapture.REQUEST_LOGGER);
  }

  @AfterEach
  void detach() {
    logs.detach();
    MDC.clear();
  }

  @Test
  void generatesARequestId_exposesItInTheHeader_andClearsTheMdc() throws Exception {
    AtomicReference<String> seenInChain = new AtomicReference<>();

    filter.doFilter(
        request, response, (rq, rs) -> seenInChain.set(MDC.get(RequestIdFilter.MDC_REQUEST_ID)));

    String header = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
    assertThat(header).startsWith("REQ-");
    assertThat(seenInChain.get()).isEqualTo(header);
    assertThat(MDC.get(RequestIdFilter.MDC_REQUEST_ID))
        .as("MDC cleared after the request")
        .isNull();
  }

  @Test
  void putsAppMethodAndPathInTheMdcDuringTheRequest() throws Exception {
    AtomicReference<String> app = new AtomicReference<>();
    AtomicReference<String> method = new AtomicReference<>();
    AtomicReference<String> path = new AtomicReference<>();

    filter.doFilter(
        request,
        response,
        (rq, rs) -> {
          app.set(MDC.get(RequestIdFilter.MDC_APP));
          method.set(MDC.get(RequestIdFilter.MDC_METHOD));
          path.set(MDC.get(RequestIdFilter.MDC_PATH));
        });

    assertThat(app.get()).isEqualTo("test-app");
    assertThat(method.get()).isEqualTo("GET");
    assertThat(path.get()).isEqualTo("/api/events/1");
  }

  @Test
  void safeClientRequestId_isKept() throws Exception {
    request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "trace_ID-123");

    filter.doFilter(request, response, (rq, rs) -> {});

    assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo("trace_ID-123");
  }

  @Test
  void unsafeClientRequestId_isReplaced() throws Exception {
    request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "bad id\nwith newline");

    filter.doFilter(request, response, (rq, rs) -> {});

    assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).startsWith("REQ-");
  }

  @Test
  void actuatorProbes_areSkipped() throws Exception {
    MockHttpServletRequest probe = new MockHttpServletRequest("GET", "/actuator/health");
    AtomicReference<Boolean> chainCalled = new AtomicReference<>(false);

    filter.doFilter(probe, response, (rq, rs) -> chainCalled.set(true));

    assertThat(chainCalled.get()).isTrue();
    assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isNull();
    assertThat(logs.entries()).isEmpty();
  }

  @Test
  void success_writesOneInfoLine() throws Exception {
    filter.doFilter(request, response, (rq, rs) -> {});

    assertThat(logs.entries())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.level()).isEqualTo(Level.INFO);
              assertThat(e.message()).startsWith("HTTP request completed: status=200");
            });
  }

  @Test
  void recordedError_isLoggedAsWarn_withStatusInTheMdc() throws Exception {
    filter.doFilter(
        request,
        response,
        (rq, rs) -> {
          RequestIdFilter.recordError(request, "Validation failed: name is required");
          ((MockHttpServletResponse) rs).setStatus(400);
        });

    assertThat(logs.entries())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.level()).isEqualTo(Level.WARN);
              assertThat(e.message()).isEqualTo("Validation failed: name is required");
              assertThat(e.mdc()).containsEntry("status", "400").containsKey("durationMs");
            });
  }

  @Test
  void errorStatusWithoutRecordedMessage_getsAGenericWarn() throws Exception {
    filter.doFilter(request, response, (rq, rs) -> ((MockHttpServletResponse) rs).setStatus(404));

    assertThat(logs.entries())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.level()).isEqualTo(Level.WARN);
              assertThat(e.message()).isEqualTo("Request failed with status 404");
            });
  }

  @Test
  void serverError_withRecordedCause_isLoggedAsErrorWithStackTrace() throws Exception {
    filter.doFilter(
        request,
        response,
        (rq, rs) -> {
          RequestIdFilter.recordError(
              request, "IllegalStateException: boom", new IllegalStateException("boom"));
          ((MockHttpServletResponse) rs).setStatus(500);
        });

    assertThat(logs.entries())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.level()).isEqualTo(Level.ERROR);
              assertThat(e.hasThrowable()).isTrue();
            });
  }

  @Test
  void escapedRuntimeException_isRethrown_andLoggedAs500() {
    assertThatThrownBy(
            () ->
                filter.doFilter(
                    request,
                    response,
                    (rq, rs) -> {
                      throw new IllegalStateException("kaboom");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(logs.entries())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.level()).isEqualTo(Level.ERROR);
              assertThat(e.message()).isEqualTo("kaboom");
              assertThat(e.mdc()).containsEntry("status", "500");
              assertThat(e.hasThrowable()).isTrue();
            });
  }

  @Test
  void escapedServletException_isUnwrappedToItsCause() {
    assertThatThrownBy(
            () ->
                filter.doFilter(
                    request,
                    response,
                    (rq, rs) -> {
                      throw new ServletException(
                          "wrapper", new IllegalArgumentException("inner problem"));
                    }))
        .isInstanceOf(ServletException.class);

    assertThat(logs.entries())
        .singleElement()
        .satisfies(e -> assertThat(e.message()).isEqualTo("inner problem"));
  }

  @Test
  void escapedExceptionWithoutMessage_usesTheClassName() {
    assertThatThrownBy(
            () ->
                filter.doFilter(
                    request,
                    response,
                    (rq, rs) -> {
                      throw new IllegalStateException();
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(logs.entries())
        .singleElement()
        .satisfies(e -> assertThat(e.message()).isEqualTo("IllegalStateException"));
  }
}
