package com.ionixx.Event_Ticket_Booking.security;

import com.ionixx.Event_Ticket_Booking.entity.User;
import com.ionixx.Event_Ticket_Booking.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtService {

  private final SecretKey key;
  private final Duration lifetime;

  public JwtService(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.expiration-minutes:60}") long expirationMinutes) {
    byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < 32) {
      throw new IllegalStateException("app.jwt.secret must be at least 32 bytes long");
    }
    this.key = Keys.hmacShaKeyFor(bytes);
    this.lifetime = Duration.ofMinutes(expirationMinutes);
  }

  public String generate(User user) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(String.valueOf(user.getId()))
        .claim("email", user.getEmail())
        .claim("role", user.getRole().name())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(lifetime)))
        .signWith(key)
        .compact();
  }

  public long expiresInSeconds() {
    return lifetime.toSeconds();
  }

  /**
   * @throws JwtException if the token is malformed, tampered with, expired or has bad claims
   */
  public AuthUser parse(String token) {
    Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    try {
      return new AuthUser(
          Long.parseLong(claims.getSubject()),
          claims.get("email", String.class),
          Role.valueOf(claims.get("role", String.class)));
    } catch (RuntimeException e) {
      throw new JwtException("Invalid token claims", e);
    }
  }
}
