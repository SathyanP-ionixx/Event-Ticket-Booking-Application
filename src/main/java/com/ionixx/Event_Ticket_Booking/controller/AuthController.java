package com.ionixx.Event_Ticket_Booking.controller;

import com.ionixx.Event_Ticket_Booking.dto.LoginRequest;
import com.ionixx.Event_Ticket_Booking.dto.LoginResponse;
import com.ionixx.Event_Ticket_Booking.dto.RegisterRequest;
import com.ionixx.Event_Ticket_Booking.dto.RegisterResponse;
import com.ionixx.Event_Ticket_Booking.filter.RequestIdFilter;
import com.ionixx.Event_Ticket_Booking.service.AuthService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  // ----------------------- register -----------------------
  @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
    RegisterResponse created = authService.register(request);
    MDC.put(RequestIdFilter.MDC_USER_ID, String.valueOf(created.userId()));
    return created;
  }

  // ----------------------- login -----------------------
  @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    LoginResponse response = authService.login(request);
    MDC.put(RequestIdFilter.MDC_USER_ID, String.valueOf(response.userId()));
    return response;
  }
}
