package com.ionixx.Event_Ticket_Booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ionixx.Event_Ticket_Booking.dto.LoginRequest;
import com.ionixx.Event_Ticket_Booking.dto.LoginResponse;
import com.ionixx.Event_Ticket_Booking.dto.RegisterRequest;
import com.ionixx.Event_Ticket_Booking.dto.RegisterResponse;
import com.ionixx.Event_Ticket_Booking.entity.User;
import com.ionixx.Event_Ticket_Booking.enums.Role;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.InvalidCredentialsException;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.UserAlreadyExistsException;
import com.ionixx.Event_Ticket_Booking.repository.UserRepository;
import com.ionixx.Event_Ticket_Booking.security.AuthUser;
import com.ionixx.Event_Ticket_Booking.security.JwtService;
import com.ionixx.Event_Ticket_Booking.support.TestData;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AuthServiceTest {

  private final UserRepository userRepository = mock(UserRepository.class);
  private final PasswordEncoder encoder =
      new BCryptPasswordEncoder(4); // cheap cost, same behaviour
  private final JwtService jwtService =
      new JwtService("auth-service-test-secret-key-0123456789-abc", 60);
  private final AuthService service = new AuthService(userRepository, encoder, jwtService);

  private static RegisterRequest register(String email, String password, String role) {
    RegisterRequest r = new RegisterRequest();
    r.setEmail(email);
    r.setPassword(password);
    r.setRole(role);
    return r;
  }

  private static LoginRequest login(String email, String password) {
    LoginRequest r = new LoginRequest();
    r.setEmail(email);
    r.setPassword(password);
    return r;
  }

  private void saveAssignsId() {
    when(userRepository.saveAndFlush(any(User.class)))
        .thenAnswer(
            inv -> {
              User u = inv.getArgument(0);
              ReflectionTestUtils.setField(u, "id", 1L);
              return u;
            });
  }

  // ------------------------------------------------------------------ register

  @Test
  void register_defaultsToCustomer_lowercasesEmail_andHashesThePassword() {
    saveAssignsId();

    RegisterResponse res = service.register(register("  Jane@Test.COM ", "password123", null));

    assertThat(res.userId()).isEqualTo(1L);
    assertThat(res.email()).isEqualTo("jane@test.com");
    assertThat(res.role()).isEqualTo(Role.CUSTOMER);

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(userRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getPassword()).isNotEqualTo("password123");
    assertThat(encoder.matches("password123", saved.getValue().getPassword())).isTrue();
  }

  @Test
  void register_roleIsCaseInsensitive() {
    saveAssignsId();

    assertThat(service.register(register("o@test.com", "password123", "organiser")).role())
        .isEqualTo(Role.ORGANISER);
  }

  @Test
  void register_existingEmail_isConflict() {
    when(userRepository.existsByEmail("jane@test.com")).thenReturn(true);

    assertThatThrownBy(() -> service.register(register("Jane@test.com", "password123", null)))
        .isInstanceOf(UserAlreadyExistsException.class);
    verify(userRepository, never()).saveAndFlush(any());
  }

  @Test
  void register_concurrentDuplicate_isMappedToConflict() {
    when(userRepository.saveAndFlush(any(User.class)))
        .thenThrow(new DataIntegrityViolationException("dup"));

    assertThatThrownBy(() -> service.register(register("jane@test.com", "password123", null)))
        .isInstanceOf(UserAlreadyExistsException.class);
  }

  // ------------------------------------------------------------------ login

  private User storedUser() {
    User u = TestData.user(5L, "jane@test.com", Role.ORGANISER);
    u.setPassword(encoder.encode("password123"));
    when(userRepository.findByEmail("jane@test.com")).thenReturn(java.util.Optional.of(u));
    return u;
  }

  @Test
  void login_success_returnsAUsableToken() {
    storedUser();

    LoginResponse res = service.login(login("  JANE@test.com ", "password123"));

    assertThat(res.userId()).isEqualTo(5L);
    assertThat(res.tokenType()).isEqualTo("Bearer");
    assertThat(res.expiresIn()).isEqualTo(3600L);
    assertThat(res.role()).isEqualTo(Role.ORGANISER);
    assertThat(jwtService.parse(res.accessToken()))
        .isEqualTo(new AuthUser(5L, "jane@test.com", Role.ORGANISER));
  }

  @Test
  void login_wrongPassword_isRejected() {
    storedUser();

    assertThatThrownBy(() -> service.login(login("jane@test.com", "wrong-password")))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage("Invalid email or password");
  }

  @Test
  void login_unknownEmail_isRejectedWithTheSameMessage() {
    when(userRepository.findByEmail("nobody@test.com")).thenReturn(java.util.Optional.empty());

    assertThatThrownBy(() -> service.login(login("nobody@test.com", "password123")))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage("Invalid email or password");
  }
}
