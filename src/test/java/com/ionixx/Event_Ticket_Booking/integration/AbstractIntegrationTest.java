package com.ionixx.Event_Ticket_Booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ionixx.Event_Ticket_Booking.filter.RequestIdFilter;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Boots the whole application against a real PostgreSQL (Testcontainers) so Liquibase, the partial
 * unique index, row locking, the security chain and the exception handler all run exactly as in
 * production. One container is shared by every test class; tests use unique data instead of
 * cleaning tables.
 */
@SpringBootTest
abstract class AbstractIntegrationTest {

  static final String JWT_SECRET = "integration-test-secret-key-0123456789-abcdef";
  static final long MISSING_ID = 987_654_321L;

  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("app.jwt.secret", () -> JWT_SECRET);
  }

  @Autowired WebApplicationContext context;
  @Autowired RequestIdFilter requestIdFilter;

  MockMvc mvc;

  @BeforeEach
  void buildMockMvc() {
    // same order as production: RequestIdFilter first, then the Spring Security chain
    mvc =
        MockMvcBuilders.webAppContextSetup(context)
            .addFilters(requestIdFilter)
            .apply(springSecurity())
            .build();
  }

  // ------------------------------------------------------------------ accounts

  record Account(long id, String email, String token) {}

  String registerBody(String email, String password, String role) {
    String roleJson = role == null ? "" : ",\"role\":\"" + role + "\"";
    return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"" + roleJson + "}";
  }

  static String uniqueEmail() {
    return "user-" + UUID.randomUUID() + "@test.com";
  }

  Account registerAndLogin(String role) throws Exception {
    String email = uniqueEmail();
    String password = "Password123";

    String registered =
        mvc.perform(
                post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerBody(email, password, role)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = ((Number) JsonPath.read(registered, "$.user_id")).longValue();

    String login =
        mvc.perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return new Account(id, email, JsonPath.read(login, "$.access_token"));
  }

  Account organiser() throws Exception {
    return registerAndLogin("ORGANISER");
  }

  Account customer() throws Exception {
    return registerAndLogin(null);
  }

  static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, Account who) {
    return request.header("Authorization", "Bearer " + who.token());
  }

  // ------------------------------------------------------------------ events

  static String eventJson(String name, String date, int seats, String price) {
    return "{\"name\":\""
        + name
        + "\",\"category\":\"MUSIC\",\"base_price\":"
        + price
        + ",\"total_seats\":"
        + seats
        + ",\"event_date\":\""
        + date
        + "\",\"venue\":\"Main Hall\"}";
  }

  static String uniqueEventName() {
    return "Event " + UUID.randomUUID();
  }

  ResultActions postEvent(Account organiser, String json) throws Exception {
    return mvc.perform(
        as(post("/api/events"), organiser).contentType(MediaType.APPLICATION_JSON).content(json));
  }

  long createEvent(Account organiser, int totalSeats, int daysAhead, String price)
      throws Exception {
    String body =
        postEvent(
                organiser,
                eventJson(
                    uniqueEventName(),
                    LocalDate.now().plusDays(daysAhead).toString(),
                    totalSeats,
                    price))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return ((Number) JsonPath.read(body, "$.id")).longValue();
  }

  /** An event 45 days away (early-bird price) with the given number of seats, base price 100.00. */
  long createEvent(Account organiser, int totalSeats) throws Exception {
    return createEvent(organiser, totalSeats, 45, "100.00");
  }

  // ------------------------------------------------------------------ bookings

  ResultActions book(Account who, long eventId, String name, String email, int... seats)
      throws Exception {
    String seatList =
        Arrays.stream(seats).mapToObj(String::valueOf).collect(Collectors.joining(","));
    return mvc.perform(
        as(post("/api/bookings"), who)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"event_id\":"
                    + eventId
                    + ",\"customer_name\":\""
                    + name
                    + "\",\"customer_email\":\""
                    + email
                    + "\",\"seat_numbers\":["
                    + seatList
                    + "]}"));
  }

  /** Books and returns the response body; asserts the 201 every booking attempt gets. */
  String bookOk(Account who, long eventId, String email, int... seats) throws Exception {
    return book(who, eventId, "John Doe", email, seats)
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  long bookingId(String body) {
    return ((Number) JsonPath.read(body, "$.id")).longValue();
  }

  ResultActions getBooking(Account who, Object id) throws Exception {
    return mvc.perform(as(get("/api/bookings/" + id), who));
  }

  // ------------------------------------------------------------------ assertions

  static void assertAmount(String json, String path, String expected) {
    Object raw = JsonPath.read(json, path);
    assertThat(new BigDecimal(String.valueOf(raw))).as(path).isEqualByComparingTo(expected);
  }
}
