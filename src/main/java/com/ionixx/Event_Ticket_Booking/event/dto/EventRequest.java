package com.ionixx.Event_Ticket_Booking.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ionixx.Event_Ticket_Booking.event.validators.FutureIsoDate;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class EventRequest {

    @NotNull(message = "is required")
    @Size(min = 3, max = 100, message = "must be between 3 and 100 characters")
    private String name;

    @NotNull(message = "is required")
    @Pattern(regexp = "MUSIC|SPORTS|TECH|THEATRE",
             flags = Pattern.Flag.CASE_INSENSITIVE,
             message = "must be one of MUSIC, SPORTS, TECH, THEATRE")
    private String category;

    @NotNull(message = "is required")
    @DecimalMin(value = "0", inclusive = false, message = "must be greater than 0")
    @JsonProperty("base_price")
    private BigDecimal basePrice;

    @NotNull(message = "is required")
    @Min(value = 10, message = "must be between 10 and 10000")
    @Max(value = 10000, message = "must be between 10 and 10000")
    @JsonProperty("total_seats")
    private Integer totalSeats;

    @NotNull(message = "is required")
    @FutureIsoDate
    @JsonProperty("event_date")
    private String eventDate;

    @NotNull(message = "is required")
    @Size(min = 3, max = 100, message = "must be between 3 and 100 characters")
    private String venue;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }
    public Integer getTotalSeats() { return totalSeats; }
    public void setTotalSeats(Integer totalSeats) { this.totalSeats = totalSeats; }
    public String getEventDate() { return eventDate; }
    public void setEventDate(String eventDate) { this.eventDate = eventDate; }
    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }
}
