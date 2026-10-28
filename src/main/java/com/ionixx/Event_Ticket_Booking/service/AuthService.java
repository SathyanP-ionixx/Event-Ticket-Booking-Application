package com.ionixx.Event_Ticket_Booking.service;

import com.ionixx.Event_Ticket_Booking.dto.LoginRequest;
import com.ionixx.Event_Ticket_Booking.dto.LoginResponse;
import com.ionixx.Event_Ticket_Booking.dto.RegisterRequest;
import com.ionixx.Event_Ticket_Booking.dto.RegisterResponse;
import com.ionixx.Event_Ticket_Booking.entity.User;
import com.ionixx.Event_Ticket_Booking.enums.Role;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.InvalidCredentialsException;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.UserAlreadyExistsException;
import com.ionixx.Event_Ticket_Booking.repository.UserRepository;
import com.ionixx.Event_Ticket_Booking.security.JwtService;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** No logging here on purpose: RequestIdFilter writes the one log line per request. */
@Service
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  // compared against when the email is unknown, so "no such user" costs the same time as "wrong
  // password"
  private final String dummyHash;

  public AuthService(
      UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.dummyHash = passwordEncoder.encode("not-a-real-password");
  }

  @Transactional
  public RegisterResponse register(RegisterRequest req) {
    String email = normalize(req.getEmail());
    if (userRepository.existsByEmail(email)) {
      throw new UserAlreadyExistsException();
    }

    User user = new User();
    user.setEmail(email);
    user.setPassword(passwordEncoder.encode(req.getPassword()));
    user.setRole(
        req.getRole() == null
            ? Role.CUSTOMER
            : Role.valueOf(req.getRole().toUpperCase(Locale.ROOT)));

    try {
      // flush so a concurrent duplicate fails here
      return RegisterResponse.from(userRepository.saveAndFlush(user));
    } catch (DataIntegrityViolationException e) {
      throw new UserAlreadyExistsException();
    }
  }

  @Transactional(readOnly = true)
  public LoginResponse login(LoginRequest req) {
    Optional<User> found = userRepository.findByEmail(normalize(req.getEmail()));
    String hash = found.map(User::getPassword).orElse(dummyHash);
    boolean passwordMatches = passwordEncoder.matches(req.getPassword(), hash);

    if (found.isEmpty() || !passwordMatches) {
      throw new InvalidCredentialsException();
    }
    User user = found.get();
    return new LoginResponse(
        user.getId(),
        jwtService.generate(user),
        "Bearer",
        jwtService.expiresInSeconds(),
        user.getRole());
  }

  private static String normalize(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }
}
