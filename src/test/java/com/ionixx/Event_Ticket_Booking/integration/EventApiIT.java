package com.ionixx.Event_Ticket_Booking.integration;

import com.ionixx.Event_Ticket_Booking.entity.Event;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API 1 (POST /api/events) and API 2 (GET /api/events/{id}). */
class EventApiIT extends AbstractApiIT {

    // =====================================================================
    // API 1 - create
    // =====================================================================

    @Test
    void create_valid_returns201_withGeneratedId_andSameShapeAsSpec() throws Exception {
        String date = inDays(60).toString();

        postJson("/api/events", eventBody("event_date", date))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Chennai Jazz Night"))
                .andExpect(jsonPath("$.venue").value("Music Academy, Chennai"))
                .andExpect(jsonPath("$.category").value("MUSIC"))
                .andExpect(jsonPath("$.event_date").value(date))
                .andExpect(jsonPath("$.total_seats").value(200))
                .andExpect(jsonPath("$.base_price").value(1500));

        assertEquals(1, eventRepository.count());
    }

    @Test
    void create_isTrimmedAndCategoryIsCaseInsensitive_storedAsUpperCase() throws Exception {
        postJson("/api/events", eventBody("name", "  Jazz Night  ", "category", "music", "venue", "  Hall A  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Jazz Night"))
                .andExpect(jsonPath("$.venue").value("Hall A"))
                .andExpect(jsonPath("$.category").value("MUSIC"));
    }

    @ParameterizedTest(name = "valid boundary {0}={1}")
    @MethodSource("validBoundaries")
    void create_validBoundaries_return201(String field, Object value) throws Exception {
        postJson("/api/events", eventBody(field, value)).andExpect(status().isCreated());
    }

    static Stream<Arguments> validBoundaries() {
        return Stream.of(
                Arguments.of("total_seats", 10),
                Arguments.of("total_seats", 10000),
                Arguments.of("name", "Abc"),
                Arguments.of("name", "n".repeat(100)),
                Arguments.of("venue", "Abc"),
                Arguments.of("venue", "v".repeat(100)),
                Arguments.of("base_price", 0.01),
                Arguments.of("base_price", 1500.5),
                Arguments.of("category", "SPORTS"),
                Arguments.of("category", "TECH"),
                Arguments.of("category", "THEATRE"),
                Arguments.of("event_date", inDays(1).toString()));
    }

    @ParameterizedTest(name = "{0} <- {1}")
    @MethodSource("invalidFields")
    void create_invalidField_returns400_namingThatField(String field, Object value) throws Exception {
        postJson("/api/events", eventBody(field, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[?(@.field=='" + field + "')]").exists());

        assertEquals(0, eventRepository.count());
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                // name
                Arguments.of("name", "AB"),
                Arguments.of("name", "n".repeat(101)),
                Arguments.of("name", "   "),
                Arguments.of("name", null),
                // venue
                Arguments.of("venue", "ab"),
                Arguments.of("venue", "v".repeat(101)),
                Arguments.of("venue", null),
                // category
                Arguments.of("category", "COMEDY"),
                Arguments.of("category", ""),
                Arguments.of("category", null),
                // event_date
                Arguments.of("event_date", inDays(-1).toString()),
                Arguments.of("event_date", inDays(0).toString()),
                Arguments.of("event_date", "20-12-2099"),
                Arguments.of("event_date", "2099-02-30"),
                Arguments.of("event_date", "tomorrow"),
                Arguments.of("event_date", null),
                // total_seats
                Arguments.of("total_seats", 5),
                Arguments.of("total_seats", 9),
                Arguments.of("total_seats", 10001),
                Arguments.of("total_seats", -1),
                Arguments.of("total_seats", null),
                // base_price
                Arguments.of("base_price", 0),
                Arguments.of("base_price", -5),
                Arguments.of("base_price", 1.234),
                Arguments.of("base_price", null));
    }

    @Test
    void create_twoInvalidFields_listsBoth_sortedByField() throws Exception {
        postJson("/api/events", eventBody("category", "COMEDY", "total_seats", 5))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details", hasSize(2)))
                .andExpect(jsonPath("$.details[*].field", contains("category", "total_seats")))
                .andExpect(jsonPath("$.details[0].message").value("must be one of MUSIC, SPORTS, TECH, THEATRE"))
                .andExpect(jsonPath("$.details[1].message").value("must be between 10 and 10000"));
    }

    @Test
    void create_emptyObject_listsAllSixFieldsAsRequired() throws Exception {
        postJson("/api/events", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[*].field",
                        contains("base_price", "category", "event_date", "name", "total_seats", "venue")))
                .andExpect(jsonPath("$.details[0].message").value("is required"));
    }

    @Test
    void create_duplicateNameAndDate_returns409_DUPLICATE_EVENT() throws Exception {
        postJson("/api/events", eventBody()).andExpect(status().isCreated());

        postJson("/api/events", eventBody())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_EVENT"));

        assertEquals(1, eventRepository.count());
    }

    @Test
    void create_duplicateIsDetectedAfterTrimming() throws Exception {
        postJson("/api/events", eventBody("name", "Jazz Night")).andExpect(status().isCreated());

        postJson("/api/events", eventBody("name", "   Jazz Night   "))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_EVENT"));
    }

    @Test
    void create_sameNameDifferentDate_or_sameDateDifferentName_isNotADuplicate() throws Exception {
        postJson("/api/events", eventBody()).andExpect(status().isCreated());

        postJson("/api/events", eventBody("event_date", inDays(61).toString())).andExpect(status().isCreated());
        postJson("/api/events", eventBody("name", "Another Show")).andExpect(status().isCreated());

        assertEquals(3, eventRepository.count());
    }

    @Test
    void create_malformedJson_returns400_INVALID_REQUEST() throws Exception {
        postJson("/api/events", "{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void create_emptyBody_returns400_INVALID_REQUEST() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/events")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void create_wrongJsonType_returns400_INVALID_REQUEST() throws Exception {
        postJson("/api/events", eventBody("total_seats", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void errorBodies_neverExposeStackTraces() throws Exception {
        postJson("/api/events", "{not json")
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    // =====================================================================
    // API 2 - get by id
    // =====================================================================

    @Test
    void get_existing_returns200_withSameShapeAsCreate() throws Exception {
        Event e = saveEvent("Chennai Jazz Night", inDays(60), 200, "1500");

        getJson("/api/events/" + e.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(e.getId()))
                .andExpect(jsonPath("$.name").value("Chennai Jazz Night"))
                .andExpect(jsonPath("$.venue").value("Music Academy, Chennai"))
                .andExpect(jsonPath("$.category").value("MUSIC"))
                .andExpect(jsonPath("$.event_date").value(inDays(60).toString()))
                .andExpect(jsonPath("$.total_seats").value(200))
                .andExpect(jsonPath("$.base_price").value(1500.0));
    }

    @Test
    void get_afterCreate_roundTrips() throws Exception {
        String response = postJson("/api/events", eventBody())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = com.jayway.jsonpath.JsonPath.read(response, "$.id");

        getJson("/api/events/" + id.longValue())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Chennai Jazz Night"));
    }

    @Test
    void get_missing_returns404_withSpecBody() throws Exception {
        getJson("/api/events/999")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Event with id 999 not found"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-5", "1.5", "12abc", "99999999999999999999"})
    void get_idNotAPositiveInteger_returns400_INVALID_REQUEST(String id) throws Exception {
        getJson("/api/events/" + id)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    // =====================================================================
    // request id header (RequestIdFilter)
    // =====================================================================

    @Test
    void everyResponse_carriesAGeneratedRequestId() throws Exception {
        getJson("/api/events/999")
                .andExpect(header().string("X-Request-Id", startsWith("REQ-")));
    }

    @Test
    void safeClientRequestId_isEchoed() throws Exception {
        mvc.perform(get("/api/events/999").header("X-Request-Id", "client-123_ABC"))
                .andExpect(header().string("X-Request-Id", "client-123_ABC"));
    }

    @Test
    void unsafeClientRequestId_isReplaced() throws Exception {
        mvc.perform(get("/api/events/999").header("X-Request-Id", "bad id with spaces!"))
                .andExpect(header().string("X-Request-Id", startsWith("REQ-")))
                .andExpect(header().string("X-Request-Id", not("bad id with spaces!")));
    }

    @Test
    void unknownRoute_isNotA500() throws Exception {
        getJson("/api/nothing-here")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }
}
