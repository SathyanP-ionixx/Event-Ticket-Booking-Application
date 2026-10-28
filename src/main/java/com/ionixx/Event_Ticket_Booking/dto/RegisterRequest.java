package com.ionixx.Event_Ticket_Booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

  @NotNull(message = "is required")
  @Size(max = 255, message = "must be at most 255 characters")
  @Pattern(
      regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
      message = "must be a valid email")
  private String email;

  @NotBlank(message = "is required")
  @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
  private String password;

  /** Optional. ORGANISER or CUSTOMER (any case). Defaults to CUSTOMER. */
  @Pattern(
      regexp = "ORGANISER|CUSTOMER",
      flags = Pattern.Flag.CASE_INSENSITIVE,
      message = "must be one of ORGANISER, CUSTOMER")
  private String role;

  public String getEmail() {
    return email;
  }

  // trimmed on the way in so validation and storage see the same value
  public void setEmail(String email) {
    this.email = email == null ? null : email.strip();
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role == null ? null : role.strip();
  }
}
