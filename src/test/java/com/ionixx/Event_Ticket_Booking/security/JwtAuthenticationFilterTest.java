package com.ionixx.Event_Ticket_Booking.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.ionixx.Event_Ticket_Booking.enums.Role;
import com.ionixx.Event_Ticket_Booking.filter.RequestIdFilter;
import com.ionixx.Event_Ticket_Booking.support.TestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

  private final JwtService jwtService = new JwtService(JwtServiceTest.SECRET, 60);
  private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);

  private final MockHttpServletRequest request = new MockHttpServletRequest();
  private final MockHttpServletResponse response = new MockHttpServletResponse();
  private final MockFilterChain chain = new MockFilterChain();

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
    MDC.clear();
  }

  private String tokenFor(Role role) {
    return jwtService.generate(TestData.user(7L, "user@test.com", role));
  }

  @Test
  void validBearerToken_authenticatesAndPutsUserIdInMdc() throws Exception {
    request.addHeader("Authorization", "Bearer " + tokenFor(Role.ORGANISER));

    filter.doFilter(request, response, chain);

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNotNull();
    assertThat(auth.getPrincipal()).isEqualTo(new AuthUser(7L, "user@test.com", Role.ORGANISER));
    assertThat(auth.getAuthorities())
        .extracting(Object::toString)
        .containsExactly("ROLE_ORGANISER");
    assertThat(MDC.get(RequestIdFilter.MDC_USER_ID)).isEqualTo("7");
    assertThat(chain.getRequest()).as("chain continued").isNotNull();
  }

  @Test
  void schemeIsCaseInsensitive() throws Exception {
    request.addHeader("Authorization", "bearer " + tokenFor(Role.CUSTOMER));

    filter.doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
  }

  @Test
  void invalidToken_staysAnonymous_andFlagsTheError() throws Exception {
    request.addHeader("Authorization", "Bearer garbage");

    filter.doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTR))
        .isEqualTo("Invalid or expired token");
    assertThat(MDC.get(RequestIdFilter.MDC_USER_ID)).isNull();
    assertThat(chain.getRequest()).as("filter never rejects by itself").isNotNull();
  }

  @Test
  void expiredToken_staysAnonymous() throws Exception {
    String expired =
        new JwtService(JwtServiceTest.SECRET, -1)
            .generate(TestData.user(7L, "u@t.com", Role.CUSTOMER));
    request.addHeader("Authorization", "Bearer " + expired);

    filter.doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTR)).isNotNull();
  }

  @Test
  void emptyToken_staysAnonymous() throws Exception {
    request.addHeader("Authorization", "Bearer ");

    filter.doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTR)).isNotNull();
  }

  @Test
  void missingHeader_staysAnonymous_withoutError() throws Exception {
    filter.doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTR)).isNull();
  }

  @Test
  void otherSchemes_areIgnored() throws Exception {
    request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

    filter.doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTR)).isNull();
  }
}
