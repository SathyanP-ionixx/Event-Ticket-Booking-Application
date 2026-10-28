package com.ionixx.Event_Ticket_Booking.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class RestSecurityHandlersTest {

  private final MockHttpServletRequest request = new MockHttpServletRequest();
  private final MockHttpServletResponse response = new MockHttpServletResponse();

  @Test
  void entryPoint_defaultMessage_is401WithBearerChallenge() throws Exception {
    new RestAuthenticationEntryPoint()
        .commence(request, response, new BadCredentialsException("x"));

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
    assertThat(response.getContentAsString())
        .isEqualTo("{\"error\":\"UNAUTHORIZED\",\"message\":\"Authentication is required\"}");
  }

  @Test
  void entryPoint_usesTheFilterErrorWhenTheTokenWasBad() throws Exception {
    request.setAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTR, "Invalid or expired token");

    new RestAuthenticationEntryPoint()
        .commence(request, response, new BadCredentialsException("x"));

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("Invalid or expired token");
  }

  @Test
  void accessDeniedHandler_is403() throws Exception {
    new RestAccessDeniedHandler().handle(request, response, new AccessDeniedException("nope"));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString())
        .isEqualTo(
            "{\"error\":\"FORBIDDEN\",\"message\":\"You do not have permission to perform this action\"}");
  }
}
