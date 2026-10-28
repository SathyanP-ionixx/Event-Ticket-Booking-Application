package com.ionixx.Event_Ticket_Booking.security;

import com.ionixx.Event_Ticket_Booking.filter.RequestIdFilter;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads "Authorization: Bearer <jwt>", and on success sets the authentication and puts userId in
 * the MDC. It never rejects a request itself: a missing or bad token just leaves the request
 * anonymous, and the security rules (RestAuthenticationEntryPoint) answer 401 where login is
 * needed.
 *
 * <p>Deliberately NOT a @Component: it is added to the security chain only (SecurityConfig).
 * RequestIdFilter runs before the security chain, so it clears the MDC after this filter.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  static final String AUTH_ERROR_ATTR = JwtAuthenticationFilter.class.getName() + ".authError";
  private static final String BEARER = "Bearer ";

  private final JwtService jwtService;

  public JwtAuthenticationFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
      String token = header.substring(BEARER.length()).trim();
      try {
        AuthUser user = jwtService.parse(token);
        var auth =
            new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
        SecurityContextHolder.getContext().setAuthentication(auth);
        MDC.put(RequestIdFilter.MDC_USER_ID, String.valueOf(user.id()));
      } catch (JwtException | IllegalArgumentException e) {
        SecurityContextHolder.clearContext();
        request.setAttribute(AUTH_ERROR_ATTR, "Invalid or expired token");
      }
    }
    chain.doFilter(request, response);
  }
}
