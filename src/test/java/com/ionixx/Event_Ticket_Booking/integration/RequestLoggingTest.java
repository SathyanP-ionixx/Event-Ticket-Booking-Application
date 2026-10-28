package com.ionixx.Event_Ticket_Booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import com.ionixx.Event_Ticket_Booking.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;

/** The user id must reach the single log line of each request (the MDC requirement). */
class RequestLoggingTest extends AbstractIntegrationTest {

  private LogCapture logs;

  @BeforeEach
  void attach() {
    logs = LogCapture.attach(LogCapture.REQUEST_LOGGER);
  }

  @AfterEach
  void detach() {
    logs.detach();
  }

  @Test
  void authenticatedRequest_logLineCarriesTheUserId() throws Exception {
    Account who = customer();
    logs.clear();

    mvc.perform(as(get("/api/events/" + MISSING_ID), who)).andExpect(status().isNotFound());

    assertThat(logs.entries())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.level()).isEqualTo(Level.WARN);
              assertThat(e.mdc())
                  .containsEntry("userId", String.valueOf(who.id()))
                  .containsEntry("method", "GET")
                  .containsEntry("path", "/api/events/" + MISSING_ID)
                  .containsEntry("eventId", String.valueOf(MISSING_ID))
                  .containsEntry("status", "404");
            });
  }

  @Test
  void successfulRequest_isOneInfoLineWithTheUserId() throws Exception {
    Account organiser = organiser();
    long eventId = createEvent(organiser, 10);
    logs.clear();

    mvc.perform(as(get("/api/events/" + eventId), organiser)).andExpect(status().isOk());

    assertThat(logs.entries())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.level()).isEqualTo(Level.INFO);
              assertThat(e.mdc()).containsEntry("userId", String.valueOf(organiser.id()));
            });
  }

  @Test
  void login_logLineCarriesTheLoggedInUserId() throws Exception {
    String email = uniqueEmail();
    mvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, "Password123", null)))
        .andExpect(status().isCreated());
    logs.clear();

    String loginBody =
        mvc.perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String userId =
        String.valueOf(
            ((Number) com.jayway.jsonpath.JsonPath.read(loginBody, "$.user_id")).longValue());

    assertThat(logs.entries())
        .singleElement()
        .satisfies(
            e ->
                assertThat(e.mdc())
                    .containsEntry("userId", userId)
                    .containsEntry("path", "/auth/login"));
  }

  @Test
  void register_logLineCarriesTheNewUserId() throws Exception {
    logs.clear();

    String body =
        mvc.perform(
                post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerBody(uniqueEmail(), "Password123", null)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String userId =
        String.valueOf(((Number) com.jayway.jsonpath.JsonPath.read(body, "$.user_id")).longValue());

    assertThat(logs.entries())
        .singleElement()
        .satisfies(e -> assertThat(e.mdc()).containsEntry("userId", userId));
  }

  @Test
  void anonymousRejection_hasNoUserId_andSecretsAreNeverLogged() throws Exception {
    logs.clear();

    mvc.perform(get("/api/events/1").header("Authorization", "Bearer not.a.jwt"))
        .andExpect(status().isUnauthorized());

    assertThat(logs.entries())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.level()).isEqualTo(Level.WARN);
              assertThat(e.message()).isEqualTo("Invalid or expired token");
              assertThat(e.mdc()).doesNotContainKey("userId");
            });
    assertThat(logs.entries().toString()).doesNotContain("not.a.jwt");
  }

  @Test
  void requestId_isEchoedInTheResponse_andTheMdcDoesNotLeakBetweenRequests() throws Exception {
    Account who = customer();

    mvc.perform(as(get("/api/events/" + MISSING_ID), who).header("X-Request-Id", "my-trace-1"))
        .andExpect(header().string("X-Request-Id", "my-trace-1"));

    assertThat(MDC.get("userId")).isNull();
    assertThat(MDC.get("requestId")).isNull();
  }
}
