package com.ionixx.Event_Ticket_Booking.security;

import com.ionixx.Event_Ticket_Booking.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** 401 for a missing / invalid / expired token, in the project's ErrorResponse format. */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
      throws IOException {
    String message =
        request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTR) instanceof String s
            ? s
            : "Authentication is required";
    RequestIdFilter.recordError(request, message);

    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    SecurityErrorWriter.write(response, 401, "UNAUTHORIZED", message);
  }
}
