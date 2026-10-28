package com.ionixx.Event_Ticket_Booking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SeatMapResponse(
    @JsonProperty("event_id") Long eventId,
    @JsonProperty("total_seats") Integer totalSeats,
    @JsonProperty("booked_seats") List<Integer> bookedSeats,
    @JsonProperty("available_seats") List<Integer> availableSeats) {}
