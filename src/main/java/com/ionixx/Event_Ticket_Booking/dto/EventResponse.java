package com.ionixx.Event_Ticket_Booking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ionixx.Event_Ticket_Booking.entity.Event;
import com.ionixx.Event_Ticket_Booking.enums.Category;
import java.math.BigDecimal;
import java.time.LocalDate;

public record EventResponse(
    Long id,
    String name,
    Category category,
    @JsonProperty("base_price") BigDecimal basePrice,
    @JsonProperty("total_seats") Integer totalSeats,
    @JsonProperty("event_date") LocalDate eventDate,
    String venue) {

  public static EventResponse from(Event e) {
    return new EventResponse(
        e.getId(),
        e.getName(),
        e.getCategory(),
        e.getBasePrice(),
        e.getTotalSeats(),
        e.getEventDate(),
        e.getVenue());
  }
}
