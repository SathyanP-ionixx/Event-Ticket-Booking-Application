package com.ionixx.Event_Ticket_Booking.security;

import com.ionixx.Event_Ticket_Booking.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 403 for a logged-in user whose role is not allowed. */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

  private static final String MESSAGE = "You do not have permission to perform this action";

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
      throws IOException {
    RequestIdFilter.recordError(request, MESSAGE);
    SecurityErrorWriter.write(response, 403, "FORBIDDEN", MESSAGE);
  }
}
