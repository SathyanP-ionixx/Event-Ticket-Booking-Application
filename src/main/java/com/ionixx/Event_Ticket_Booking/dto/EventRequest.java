package com.ionixx.Event_Ticket_Booking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ionixx.Event_Ticket_Booking.validators.FutureIsoDate;
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
    @Digits(integer = 10, fraction = 2, message = "must have at most 10 digits before and 2 after the decimal point")
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
    // text fields are trimmed on the way in, so validation, duplicate check and storage all see the same value
    public void setName(String name) { this.name = trim(name); }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = trim(category); }
    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }
    public Integer getTotalSeats() { return totalSeats; }
    public void setTotalSeats(Integer totalSeats) { this.totalSeats = totalSeats; }
    public String getEventDate() { return eventDate; }
    public void setEventDate(String eventDate) { this.eventDate = trim(eventDate); }
    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = trim(venue); }

    private static String trim(String value) {
        return value == null ? null : value.strip();
    }
}