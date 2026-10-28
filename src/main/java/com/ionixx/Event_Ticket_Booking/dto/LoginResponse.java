package com.ionixx.Event_Ticket_Booking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ionixx.Event_Ticket_Booking.enums.Role;

public record LoginResponse(
    @JsonProperty("user_id") Long userId,
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("token_type") String tokenType,
    @JsonProperty("expires_in") long expiresIn,
    Role role) {}
