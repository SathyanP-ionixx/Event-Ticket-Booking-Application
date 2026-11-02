package com.ionixx.Event_Ticket_Booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ionixx.Event_Ticket_Booking.entity.Event;
import com.ionixx.Event_Ticket_Booking.enums.Category;
import com.ionixx.Event_Ticket_Booking.repository.EventRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class EventApiTest extends AbstractIntegrationTest {

  @Autowired EventRepository eventRepository;

  private ResultActions putEvent(Account who, Object id, String json) throws Exception {
    return mvc.perform(
        as(put("/api/events/" + id), who).contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private static long idOf(String json) {
    return ((Number) com.jayway.jsonpath.JsonPath.read(json, "$.id")).longValue();
  }

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

  // ------------------------------------------------------------------ update (PUT)

  @Test
  void update_owner_replacesEveryField_andEveryoneSeesIt() throws Exception {
    Account organiser = organiser();
    long id = createEvent(organiser, 50);
    String newName = uniqueEventName();
    String newDate = LocalDate.now().plusDays(90).toString();
    String body =
        "{\"name\":\""
            + newName
            + "\",\"category\":\"TECH\",\"base_price\":250.50,"
            + "\"total_seats\":80,\"event_date\":\""
            + newDate
            + "\",\"venue\":\"New Venue\"}";

    String json =
        putEvent(organiser, id, body)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.name").value(newName))
            .andExpect(jsonPath("$.category").value("TECH"))
            .andExpect(jsonPath("$.total_seats").value(80))
            .andExpect(jsonPath("$.event_date").value(newDate))
            .andExpect(jsonPath("$.venue").value("New Venue"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertAmount(json, "$.base_price", "250.50");

    mvc.perform(as(get("/api/events/" + id), customer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value(newName))
        .andExpect(jsonPath("$.venue").value("New Venue"));
    // the owner did not change: the organiser still reads the reports of the edited event
    mvc.perform(as(get("/api/events/" + id + "/summary"), organiser))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_seats").value(80));
  }

  @Test
  void update_leavesThePriceOfExistingBookingsAlone() throws Exception {
    Account organiser = organiser();
    long id = createEvent(organiser, 20); // base 100.00, 45 days away
    Account customer = customer();
    long bookingId =
        bookingId(bookOk(customer, id, "keep@example.com", 1)); // 90.00 with early bird

    putEvent(
            organiser,
            id,
            eventJson(uniqueEventName(), LocalDate.now().plusDays(5).toString(), 20, "500.00"))
        .andExpect(status().isOk());

    String booking =
        getBooking(customer, bookingId)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertAmount(booking, "$.price_per_seat", "90.00");
    assertAmount(booking, "$.total_amount", "90.00");
  }

  @Test
  void update_anotherOrganiser_isForbidden_andTheEventIsUntouched() throws Exception {
    Account owner = organiser();
    long id = createEvent(owner, 50);

    putEvent(
            organiser(),
            id,
            eventJson(uniqueEventName(), LocalDate.now().plusDays(60).toString(), 50, "10.00"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("FORBIDDEN"));

    mvc.perform(as(get("/api/events/" + id), owner))
        .andExpect(jsonPath("$.venue").value("Main Hall"))
        .andExpect(jsonPath("$.total_seats").value(50));
  }

  @Test
  void update_customerIs403_andNoTokenIs401() throws Exception {
    long id = createEvent(organiser(), 50);
    String body =
        eventJson(uniqueEventName(), LocalDate.now().plusDays(60).toString(), 50, "10.00");

    putEvent(customer(), id, body).andExpect(status().isForbidden());
    mvc.perform(put("/api/events/" + id).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void update_unknownEvent_is404() throws Exception {
    putEvent(
            organiser(),
            MISSING_ID,
            eventJson(uniqueEventName(), LocalDate.now().plusDays(60).toString(), 50, "10.00"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("Event with id " + MISSING_ID + " not found"));
  }

  @Test
  void update_eventWithoutAnOwner_isForbidden() throws Exception {
    Event legacy = new Event();
    legacy.setName(uniqueEventName());
    legacy.setCategory(Category.SPORTS);
    legacy.setBasePrice(new BigDecimal("20.00"));
    legacy.setTotalSeats(30);
    legacy.setEventDate(LocalDate.now().plusDays(20));
    legacy.setVenue("Legacy Arena"); // organiserId stays null
    long id = eventRepository.save(legacy).getId();

    putEvent(
            organiser(),
            id,
            eventJson(uniqueEventName(), LocalDate.now().plusDays(60).toString(), 50, "10.00"))
        .andExpect(status().isForbidden());
  }

  @Test
  void update_badPathIds_are400() throws Exception {
    Account organiser = organiser();
    String body =
        eventJson(uniqueEventName(), LocalDate.now().plusDays(60).toString(), 50, "10.00");

    for (String id : new String[] {"abc", "0", "-1"}) {
      putEvent(organiser, id, body)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }
  }

  @Test
  void update_usesTheSameValidationAsCreate() throws Exception {
    Account organiser = organiser();
    long id = createEvent(organiser, 50);

    putEvent(organiser, id, "{}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.details.length()").value(6));

    putEvent(organiser, id, eventJson(uniqueEventName(), "2020-01-01", 50, "10.00"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.details[?(@.field=='event_date')].message").value("must be a future date"));

    putEvent(organiser, id, "{bad json")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
  }

  @Test
  void update_toTheNameAndDateOfAnotherEvent_is409_butKeepingYourOwnIsFine() throws Exception {
    Account organiser = organiser();
    String date = LocalDate.now().plusDays(60).toString();
    String nameA = uniqueEventName();
    String nameB = uniqueEventName();
    postEvent(organiser, eventJson(nameA, date, 20, "10.00")).andExpect(status().isCreated());
    long b =
        idOf(
            postEvent(organiser, eventJson(nameB, date, 20, "10.00"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

    putEvent(organiser, b, eventJson(nameA, date, 20, "10.00"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("DUPLICATE_EVENT"));

    mvc.perform(as(get("/api/events/" + b), organiser)).andExpect(jsonPath("$.name").value(nameB));

    putEvent(organiser, b, eventJson(nameB, date, 25, "12.00")) // same name + date as itself
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_seats").value(25));
  }

  @Test
  void update_cannotShrinkBelowASoldSeat_butCanGrow() throws Exception {
    Account organiser = organiser();
    long id = createEvent(organiser, 30);
    bookOk(customer(), id, "seat@example.com", 25);
    String date = LocalDate.now().plusDays(45).toString();

    putEvent(organiser, id, eventJson(uniqueEventName(), date, 20, "100.00"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.details[0].field").value("total_seats"))
        .andExpect(
            jsonPath("$.details[0].message")
                .value("must be at least 25 (seat 25 is already booked)"));

    putEvent(organiser, id, eventJson(uniqueEventName(), date, 25, "100.00"))
        .andExpect(status().isOk());

    putEvent(organiser, id, eventJson(uniqueEventName(), date, 100, "100.00"))
        .andExpect(status().isOk());
    mvc.perform(as(get("/api/events/" + id + "/seats"), organiser))
        .andExpect(jsonPath("$.total_seats").value(100))
        .andExpect(jsonPath("$.booked_seats[0]").value(25));
  }

  @Test
  void update_onlyConfirmedSeatsPinTheSize_notRejectedAttempts() throws Exception {
    Account organiser = organiser();
    long id = createEvent(organiser, 30);
    bookOk(customer(), id, "first@example.com", 10); // CONFIRMED, highest sold seat = 10
    // REJECTED (seat 10 is taken), but its stored seat rows still say 10 and 29
    String rejected = bookOk(customer(), id, "second@example.com", 10, 29);
    assertThat((String) com.jayway.jsonpath.JsonPath.read(rejected, "$.status"))
        .isEqualTo("REJECTED");

    putEvent(
            organiser,
            id,
            eventJson(uniqueEventName(), LocalDate.now().plusDays(45).toString(), 20, "100.00"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_seats").value(20));
  }

  @Test
  void update_eventThatIsAlreadyOver_is409() throws Exception {
    Account organiser = organiser();
    Event past = new Event();
    past.setName(uniqueEventName());
    past.setCategory(Category.TECH);
    past.setBasePrice(new BigDecimal("50.00"));
    past.setTotalSeats(20);
    past.setEventDate(LocalDate.now().minusDays(1));
    past.setVenue("Old Hall");
    past.setOrganiserId(organiser.id());
    long id = eventRepository.save(past).getId();

    putEvent(
            organiser,
            id,
            eventJson(uniqueEventName(), LocalDate.now().plusDays(30).toString(), 20, "10.00"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("EVENT_ALREADY_OVER"))
        .andExpect(
            jsonPath("$.message").value("Event is already over and can no longer be edited"));
  }
}
