package com.ionixx.Event_Ticket_Booking.event.dto;

/** Internal result of creating an event: the API payload plus the id used to correlate log lines. */
public record CreatedEvent(EventResponse response, String eventLogId) {
}
