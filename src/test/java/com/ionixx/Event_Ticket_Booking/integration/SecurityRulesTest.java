package com.ionixx.Event_Ticket_Booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;

/** The 401 / 403 / 200 matrix for every endpoint. */
class SecurityRulesTest extends AbstractIntegrationTest {

  // ------------------------------------------------------------------ 401

  @Test
  void protectedEndpoints_withoutAToken_are401() throws Exception {
    String[] getUrls = {
      "/api/events/1",
      "/api/events/1/seats",
      "/api/events/1/bookings",
      "/api/events/1/summary",
      "/api/bookings/1"
    };
    for (String url : getUrls) {
      mvc.perform(get(url))
          .andExpect(status().isUnauthorized())
          .andExpect(header().string("WWW-Authenticate", "Bearer"))
          .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
          .andExpect(jsonPath("$.message").value("Authentication is required"));
    }
    mvc.perform(post("/api/events").contentType("application/json").content("{}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/bookings").contentType("application/json").content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void garbageToken_is401_withTheTokenMessage() throws Exception {
    mvc.perform(get("/api/events/1").header("Authorization", "Bearer not.a.jwt"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.message").value("Invalid or expired token"));
  }

  @Test
  void expiredToken_is401() throws Exception {
    String expired =
        Jwts.builder()
            .subject("1")
            .claim("email", "e@x.com")
            .claim("role", "CUSTOMER")
            .expiration(Date.from(Instant.now().minusSeconds(60)))
            .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();

    mvc.perform(get("/api/events/1").header("Authorization", "Bearer " + expired))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Invalid or expired token"));
  }

  @Test
  void tokenSignedWithAnotherSecret_is401() throws Exception {
    String forged =
        Jwts.builder()
            .subject("1")
            .claim("email", "e@x.com")
            .claim("role", "ORGANISER")
            .expiration(Date.from(Instant.now().plusSeconds(600)))
            .signWith(
                Keys.hmacShaKeyFor(
                    "some-other-secret-key-which-is-long-enough-123"
                        .getBytes(StandardCharsets.UTF_8)))
            .compact();

    mvc.perform(
            post("/api/events")
                .header("Authorization", "Bearer " + forged)
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void nonBearerScheme_isTreatedAsNoCredentials() throws Exception {
    mvc.perform(get("/api/events/1").header("Authorization", "Basic dXNlcjpwYXNz"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Authentication is required"));
  }

  // ------------------------------------------------------------------ public

  @Test
  void publicEndpoints_needNoToken() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    // reachable without a token: answers 400 (empty body), not 401
    mvc.perform(post("/auth/login").contentType("application/json"))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/auth/register").contentType("application/json"))
        .andExpect(status().isBadRequest());
  }

  // ------------------------------------------------------------------ 403

  @Test
  void customer_cannotCreateEvents() throws Exception {
    mvc.perform(
            as(post("/api/events"), customer())
                .contentType("application/json")
                .content(eventJson(uniqueEventName(), "2099-01-01", 100, "10.00")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("FORBIDDEN"))
        .andExpect(
            jsonPath("$.message").value("You do not have permission to perform this action"));
  }

  @Test
  void customer_cannotReadReports() throws Exception {
    Account customer = customer();

    mvc.perform(as(get("/api/events/" + MISSING_ID + "/bookings"), customer))
        .andExpect(status().isForbidden());
    mvc.perform(as(get("/api/events/" + MISSING_ID + "/summary"), customer))
        .andExpect(status().isForbidden());
  }

  // ------------------------------------------------------------------ any logged-in user

  @Test
  void anyLoggedInUser_canViewEventsAndSeats() throws Exception {
    Account organiser = organiser();
    long eventId = createEvent(organiser, 10);

    for (Account who : new Account[] {customer(), organiser}) {
      mvc.perform(as(get("/api/events/" + eventId), who))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(eventId));
      mvc.perform(as(get("/api/events/" + eventId + "/seats"), who))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.available_seats.length()").value(10));
    }
  }

  @Test
  void anyLoggedInUser_canBook_includingOrganisers() throws Exception {
    Account organiser = organiser();
    long eventId = createEvent(organiser, 10);

    book(customer(), eventId, "John Doe", uniqueEmail(), 1)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
    book(organiser, eventId, "Olivia Organiser", uniqueEmail(), 2)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
  }

  // ------------------------------------------------------------------ framework errors keep the
  // JSON shape

  @Test
  void unknownRoute_isA404InTheProjectFormat() throws Exception {
    mvc.perform(as(get("/api/does-not-exist"), customer()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  @Test
  void unsupportedMethod_isA405InTheProjectFormat() throws Exception {
    mvc.perform(as(delete("/api/events/1"), customer()))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
  }
}
