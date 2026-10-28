package com.ionixx.Event_Ticket_Booking.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ionixx.Event_Ticket_Booking.enums.Role;
import com.ionixx.Event_Ticket_Booking.support.TestData;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  static final String SECRET = "unit-test-secret-key-with-more-than-32-bytes!!";
  private final JwtService service = new JwtService(SECRET, 60);

  private static String signedWith(
      String secret, String subject, Object role, long expiresInSeconds) {
    SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    var builder =
        Jwts.builder()
            .subject(subject)
            .claim("email", "x@y.com")
            .expiration(Date.from(Instant.now().plusSeconds(expiresInSeconds)));
    if (role != null) {
      builder.claim("role", role);
    }
    return builder.signWith(key).compact();
  }

  @Test
  void generateThenParse_roundTrips() {
    String token = service.generate(TestData.user(42L, "a@b.com", Role.ORGANISER));

    assertThat(service.parse(token)).isEqualTo(new AuthUser(42L, "a@b.com", Role.ORGANISER));
  }

  @Test
  void expiresInSeconds_isConfiguredMinutesTimes60() {
    assertThat(new JwtService(SECRET, 15).expiresInSeconds()).isEqualTo(900L);
  }

  @Test
  void shortSecret_isRejectedAtStartup() {
    assertThatThrownBy(() -> new JwtService("too-short", 60))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("32 bytes");
  }

  @Test
  void tokenSignedWithAnotherKey_isRejected() {
    String foreign =
        new JwtService("a-completely-different-secret-key-0123456789", 60)
            .generate(TestData.user(1L, "a@b.com", Role.CUSTOMER));

    assertThatThrownBy(() -> service.parse(foreign)).isInstanceOf(JwtException.class);
  }

  @Test
  void expiredToken_isRejected() {
    String expired =
        new JwtService(SECRET, -1).generate(TestData.user(1L, "a@b.com", Role.CUSTOMER));

    assertThatThrownBy(() -> service.parse(expired)).isInstanceOf(JwtException.class);
  }

  @Test
  void garbage_isRejected() {
    assertThatThrownBy(() -> service.parse("not-a-token")).isInstanceOf(JwtException.class);
  }

  @Test
  void unknownRoleClaim_isRejectedAsBadClaims() {
    String token = signedWith(SECRET, "5", "SUPERADMIN", 60);

    assertThatThrownBy(() -> service.parse(token))
        .isInstanceOf(JwtException.class)
        .hasMessage("Invalid token claims");
  }

  @Test
  void missingRoleClaim_isRejectedAsBadClaims() {
    assertThatThrownBy(() -> service.parse(signedWith(SECRET, "5", null, 60)))
        .isInstanceOf(JwtException.class)
        .hasMessage("Invalid token claims");
  }

  @Test
  void nonNumericSubject_isRejectedAsBadClaims() {
    assertThatThrownBy(() -> service.parse(signedWith(SECRET, "abc", "CUSTOMER", 60)))
        .isInstanceOf(JwtException.class)
        .hasMessage("Invalid token claims");
  }
}
