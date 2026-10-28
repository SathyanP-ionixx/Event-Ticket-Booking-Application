package com.ionixx.Event_Ticket_Booking.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Writes the same JSON shape as ErrorResponse without needing an ObjectMapper bean. */
final class SecurityErrorWriter {

  private SecurityErrorWriter() {}

  static void write(HttpServletResponse response, int status, String error, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response
        .getWriter()
        .write("{\"error\":\"" + escape(error) + "\",\"message\":\"" + escape(message) + "\"}");
  }

  private static String escape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
