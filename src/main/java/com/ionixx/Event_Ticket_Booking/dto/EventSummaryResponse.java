package com.ionixx.Event_Ticket_Booking.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.ionixx.Event_Ticket_Booking.enums.DemandBand;

import java.math.BigDecimal;

public record EventSummaryResponse(
        @JsonProperty("event_id") Long eventId,
        @JsonProperty("event_name") String eventName,
        @JsonProperty("total_seats") Integer totalSeats,
        @JsonProperty("seats_sold") long seatsSold,
        @JsonProperty("seats_available") long seatsAvailable,
        @JsonProperty("confirmed_bookings") long confirmedBookings,
        @JsonProperty("rejected_bookings") long rejectedBookings,
        @JsonProperty("total_revenue") BigDecimal totalRevenue,
        @JsonProperty("occupancy_pct") BigDecimal occupancyPct,
        @JsonProperty("demand_band") DemandBand demandBand
) {}
