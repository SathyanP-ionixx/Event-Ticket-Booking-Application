package com.ionixx.Event_Ticket_Booking.security;

import com.ionixx.Event_Ticket_Booking.enums.Role;

/** The authenticated caller, built from the JWT (no DB lookup per request). */
public record AuthUser(Long id, String email, Role role) {}
