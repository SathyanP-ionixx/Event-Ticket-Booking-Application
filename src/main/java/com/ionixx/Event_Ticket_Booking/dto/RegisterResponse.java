package com.ionixx.Event_Ticket_Booking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ionixx.Event_Ticket_Booking.entity.User;
import com.ionixx.Event_Ticket_Booking.enums.Role;

public record RegisterResponse(@JsonProperty("user_id") Long userId, String email, Role role) {
  public static RegisterResponse from(User u) {
    return new RegisterResponse(u.getId(), u.getEmail(), u.getRole());
  }
}
