package com.ionixx.Event_Ticket_Booking.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DtoTest {

  @Test
  void errorResponse_factories() {
    assertThat(ErrorResponse.of("X")).isEqualTo(new ErrorResponse("X", null, null));
    assertThat(ErrorResponse.of("X", "msg").message()).isEqualTo("msg");

    ErrorResponse v = ErrorResponse.validation(List.of(new ErrorResponse.FieldDetail("f", "m")));
    assertThat(v.error()).isEqualTo("VALIDATION_FAILED");
    assertThat(v.details()).hasSize(1);
  }

  @Test
  void eventRequest_trimsText_andToleratesNulls() {
    EventRequest r = new EventRequest();
    r.setName("  Name ");
    r.setCategory(" music ");
    r.setEventDate(" 2099-01-01 ");
    r.setVenue(" Hall ");

    assertThat(r.getName()).isEqualTo("Name");
    assertThat(r.getCategory()).isEqualTo("music");
    assertThat(r.getEventDate()).isEqualTo("2099-01-01");
    assertThat(r.getVenue()).isEqualTo("Hall");

    r.setName(null);
    r.setCategory(null);
    r.setEventDate(null);
    r.setVenue(null);
    assertThat(r.getName()).isNull();
    assertThat(r.getCategory()).isNull();
    assertThat(r.getEventDate()).isNull();
    assertThat(r.getVenue()).isNull();
  }

  @Test
  void loginRequest_trimsEmailButNotPassword() {
    LoginRequest r = new LoginRequest();
    r.setEmail("  a@b.com ");
    r.setPassword(" secret ");

    assertThat(r.getEmail()).isEqualTo("a@b.com");
    assertThat(r.getPassword()).isEqualTo(" secret ");

    r.setEmail(null);
    assertThat(r.getEmail()).isNull();
  }

  @Test
  void registerRequest_trimsEmailAndRole() {
    RegisterRequest r = new RegisterRequest();
    r.setEmail(" a@b.com ");
    r.setRole(" organiser ");
    r.setPassword("password123");

    assertThat(r.getEmail()).isEqualTo("a@b.com");
    assertThat(r.getRole()).isEqualTo("organiser");
    assertThat(r.getPassword()).isEqualTo("password123");

    r.setEmail(null);
    r.setRole(null);
    assertThat(r.getEmail()).isNull();
    assertThat(r.getRole()).isNull();
  }
}
