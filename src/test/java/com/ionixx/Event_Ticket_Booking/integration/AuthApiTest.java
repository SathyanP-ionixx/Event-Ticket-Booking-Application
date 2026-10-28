package com.ionixx.Event_Ticket_Booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AuthApiTest extends AbstractIntegrationTest {

  private String login(String email, String password) {
    return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
  }

  // ------------------------------------------------------------------ register

  @Test
  void register_defaultsToCustomer_andNormalisesTheEmail() throws Exception {
    String email = uniqueEmail();

    mvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("  " + email.toUpperCase() + " ", "Password123", null)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.user_id").isNumber())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.role").value("CUSTOMER"))
        .andExpect(jsonPath("$.password").doesNotExist());
  }

  @Test
  void register_organiser_roleIsCaseInsensitive() throws Exception {
    mvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(uniqueEmail(), "Password123", "organiser")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.role").value("ORGANISER"));
  }

  @Test
  void register_sameEmailTwice_isConflict_evenWithDifferentCase() throws Exception {
    String email = uniqueEmail();
    mvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, "Password123", null)))
        .andExpect(status().isCreated());

    mvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email.toUpperCase(), "Password123", null)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("USER_ALREADY_EXISTS"));
  }

  @Test
  void register_invalidFields_listEveryProblem_sortedByField() throws Exception {
    mvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("not-an-email", "short", "ADMIN")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.details.length()").value(3))
        .andExpect(jsonPath("$.details[0].field").value("email"))
        .andExpect(jsonPath("$.details[0].message").value("must be a valid email"))
        .andExpect(jsonPath("$.details[1].field").value("password"))
        .andExpect(jsonPath("$.details[2].field").value("role"));
  }

  @Test
  void register_missingFields_areRequired() throws Exception {
    mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[0].field").value("email"))
        .andExpect(jsonPath("$.details[0].message").value("is required"))
        .andExpect(jsonPath("$.details[1].field").value("password"));
  }

  // ------------------------------------------------------------------ login

  @Test
  void login_returnsABearerToken() throws Exception {
    String email = uniqueEmail();
    mvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, "Password123", "ORGANISER")))
        .andExpect(status().isCreated());

    mvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(login(email, "Password123")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access_token").isNotEmpty())
        .andExpect(jsonPath("$.token_type").value("Bearer"))
        .andExpect(jsonPath("$.expires_in").value(3600))
        .andExpect(jsonPath("$.role").value("ORGANISER"))
        .andExpect(jsonPath("$.user_id").isNumber());
  }

  @Test
  void login_emailIsCaseInsensitive() throws Exception {
    String email = uniqueEmail();
    mvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, "Password123", null)))
        .andExpect(status().isCreated());

    mvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(login(email.toUpperCase(), "Password123")))
        .andExpect(status().isOk());
  }

  @Test
  void login_wrongPassword_andUnknownEmail_lookIdentical() throws Exception {
    String email = uniqueEmail();
    mvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, "Password123", null)))
        .andExpect(status().isCreated());

    mvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(login(email, "WrongPassword1")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.message").value("Invalid email or password"));

    mvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(login(uniqueEmail(), "Password123")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.message").value("Invalid email or password"));
  }

  @Test
  void login_missingFields_areValidationErrors() throws Exception {
    mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.details.length()").value(2));
  }

  // ------------------------------------------------------------------ bad bodies

  @Test
  void malformedJson_is400() throws Exception {
    mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{bad json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("Malformed or unreadable request body"));
  }

  @Test
  void emptyBody_is400() throws Exception {
    mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("Request body is required"));
  }

  @Test
  void wrongContentType_is415() throws Exception {
    mvc.perform(post("/auth/login").contentType(MediaType.TEXT_PLAIN).content("x"))
        .andExpect(status().isUnsupportedMediaType());
  }
}
