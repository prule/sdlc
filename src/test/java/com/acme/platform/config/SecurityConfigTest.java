package com.acme.platform.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.platform.availability.adapters.in.web.PingController;
import com.acme.platform.availability.application.port.in.CheckAvailabilityUseCase;
import com.acme.platform.availability.domain.model.Availability;
import com.acme.platform.availability.domain.model.AvailabilityStatus;
import com.acme.platform.web.PlatformWebTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Security config (design D4): public, stateless, read-only, with baseline security headers. */
@PlatformWebTest(controllers = PingController.class)
class SecurityConfigTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CheckAvailabilityUseCase checkAvailabilityUseCase;

  @Test
  void writeAttemptOnPingIsRefusedNotAuthenticationChallenged() throws Exception {
    mockMvc.perform(post("/ping")).andExpect(status().isMethodNotAllowed());
  }

  @Test
  void baselineSecurityHeadersArePresent() throws Exception {
    BDDMockito.given(checkAvailabilityUseCase.checkAvailability())
        .willReturn(new Availability(AvailabilityStatus.UP));

    MvcResult result =
        mockMvc
            .perform(get("/ping"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().exists("Content-Security-Policy"))
            .andReturn();

    String csp = result.getResponse().getHeader("Content-Security-Policy");
    // No script-src directive is present, so scripts fall back to default-src, which must not
    // allow 'unsafe-inline'. style-src is the only directive permitted to (Swagger UI needs it).
    org.assertj.core.api.Assertions.assertThat(csp).contains("default-src 'self';");
    org.assertj.core.api.Assertions.assertThat(csp).doesNotContain("script-src");
  }

  @Test
  void anArbitraryBearerTokenIsIgnoredNotValidated() throws Exception {
    BDDMockito.given(checkAvailabilityUseCase.checkAvailability())
        .willReturn(new Availability(AvailabilityStatus.UP));

    mockMvc.perform(get("/ping").header("Authorization", "Bearer x")).andExpect(status().isOk());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/no-such-thing", "/swagger-ui/index.html"})
  void baselineSecurityHeadersArePresentOnAProblemAndOnTheBrowsableDescription(String path)
      throws Exception {
    mockMvc
        .perform(get(path))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().exists("Content-Security-Policy"));
  }

  @Test
  void hstsIsPresentOnlyWhenForwardedAsHttps() throws Exception {
    BDDMockito.given(checkAvailabilityUseCase.checkAvailability())
        .willReturn(new Availability(AvailabilityStatus.UP));

    mockMvc
        .perform(get("/ping").header("X-Forwarded-Proto", "https"))
        .andExpect(header().exists("Strict-Transport-Security"));

    mockMvc.perform(get("/ping")).andExpect(header().doesNotExist("Strict-Transport-Security"));
  }

  @Test
  void foreignOriginGetsNoCorsGrant() throws Exception {
    BDDMockito.given(checkAvailabilityUseCase.checkAvailability())
        .willReturn(new Availability(AvailabilityStatus.UP));

    mockMvc
        .perform(get("/ping").header("Origin", "https://other.example.test"))
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
  }
}
