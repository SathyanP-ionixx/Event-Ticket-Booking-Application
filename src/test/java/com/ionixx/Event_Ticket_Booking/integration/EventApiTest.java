package com.ionixx.Event_Ticket_Booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class EventApiTest extends AbstractIntegrationTest {

  private static String futureDate() {
    return LocalDate.now().plusDays(60).toString();
  }

  @Test
  void organiser_createsAnEvent_andAnyoneCanReadIt() throws Exception {
    Account organiser = organiser();
    String name = uniqueEventName();

    String json =
        postEvent(organiser, eventJson(name, futureDate(), 120, "150.50"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.name").value(name))
            .andExpect(jsonPath("$.category").value("MUSIC"))
            .andExpect(jsonPath("$.total_seats").value(120))
            .andExpect(jsonPath("$.event_date").value(futureDate()))
            .andExpect(jsonPath("$.venue").value("Main Hall"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertAmount(json, "$.base_price", "150.50");

    long id = ((Number) com.jayway.jsonpath.JsonPath.read(json, "$.id")).longValue();
    mvc.perform(as(get("/api/events/" + id), customer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value(name));
  }

  @Test
  void duplicateNameAndDate_isConflict() throws Exception {
    Account organiser = organiser();
    String body = eventJson(uniqueEventName(), futureDate(), 50, "10.00");
    postEvent(organiser, body).andExpect(status().isCreated());

    postEvent(organiser, body)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("DUPLICATE_EVENT"))
        .andExpect(jsonPath("$.message").value("Event with same name and date is not allowed"));
  }

  @Test
  void sameNameOnAnotherDate_isFine() throws Exception {
    Account organiser = organiser();
    String name = uniqueEventName();
    postEvent(organiser, eventJson(name, LocalDate.now().plusDays(10).toString(), 50, "10.00"))
        .andExpect(status().isCreated());
    postEvent(organiser, eventJson(name, LocalDate.now().plusDays(11).toString(), 50, "10.00"))
        .andExpect(status().isCreated());
  }

  @Test
  void emptyBody_reportsEverythingRequired() throws Exception {
    postEvent(organiser(), "{}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.details.length()").value(6))
        .andExpect(jsonPath("$.details[0].field").value("base_price"))
        .andExpect(jsonPath("$.details[5].field").value("venue"));
  }

  @Test
  void badValues_areReportedPerField() throws Exception {
    String body =
        "{\"name\":\"ab\",\"category\":\"FOOD\",\"base_price\":0,\"total_seats\":5,"
            + "\"event_date\":\"2020-01-01\",\"venue\":\"x\"}";

    postEvent(organiser(), body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.length()").value(6))
        .andExpect(
            jsonPath("$.details[?(@.field=='event_date')].message").value("must be a future date"))
        .andExpect(
            jsonPath("$.details[?(@.field=='category')].message")
                .value("must be one of MUSIC, SPORTS, TECH, THEATRE"))
        .andExpect(
            jsonPath("$.details[?(@.field=='total_seats')].message")
                .value("must be between 10 and 10000"));
  }

  @Test
  void wrongDateFormat_isReported() throws Exception {
    postEvent(organiser(), eventJson(uniqueEventName(), "01/01/2099", 50, "10.00"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.details[?(@.field=='event_date')].message")
                .value("must be in format yyyy-mm-dd"));
  }

  @Test
  void getEvent_unknownId_is404() throws Exception {
    mvc.perform(as(get("/api/events/" + MISSING_ID), customer()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("Event with id " + MISSING_ID + " not found"));
  }

  @Test
  void badIds_are400_onEveryEventRoute() throws Exception {
    Account organiser = organiser();
    for (String id : new String[] {"abc", "0", "-1"}) {
      for (String suffix : new String[] {"", "/seats", "/bookings", "/summary"}) {
        mvc.perform(as(get("/api/events/" + id + suffix), organiser))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.message").value("ID must be a positive number"));
      }
    }
  }
}
