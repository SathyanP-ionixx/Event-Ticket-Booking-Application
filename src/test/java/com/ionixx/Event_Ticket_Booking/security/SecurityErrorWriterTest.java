package com.ionixx.Event_Ticket_Booking.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityErrorWriterTest {

  @Test
  void writesStatusContentTypeAndJsonBody() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    SecurityErrorWriter.write(response, 401, "UNAUTHORIZED", "Authentication is required");

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).startsWith("application/json");
    assertThat(response.getContentAsString())
        .isEqualTo("{\"error\":\"UNAUTHORIZED\",\"message\":\"Authentication is required\"}");
  }

  @Test
  void escapesQuotesAndBackslashes() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    SecurityErrorWriter.write(response, 403, "FORBIDDEN", "say \"hi\" \\ now");

    assertThat(response.getContentAsString())
        .isEqualTo("{\"error\":\"FORBIDDEN\",\"message\":\"say \\\"hi\\\" \\\\ now\"}");
  }
}
