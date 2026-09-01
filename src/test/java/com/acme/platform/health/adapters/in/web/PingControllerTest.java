package com.acme.platform.health.adapters.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.common.error.GlobalExceptionHandler;
import com.acme.common.security.ProblemAccessDeniedHandler;
import com.acme.common.security.ProblemAuthenticationEntryPoint;
import com.acme.common.security.SecurityConfig;
import com.acme.platform.health.application.port.in.PingUseCase;
import com.acme.platform.health.domain.model.PingStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-slice test for {@link PingController}, exercising the public liveness endpoint requirement,
 * the correlation-id contract, and the security public-endpoint guarantee. {@link PingUseCase} is
 * mocked at the port seam.
 */
@WebMvcTest(PingController.class)
@Import({
  SecurityConfig.class,
  ProblemAuthenticationEntryPoint.class,
  ProblemAccessDeniedHandler.class,
  GlobalExceptionHandler.class
})
class PingControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PingUseCase pingUseCase;

  @Test
  void ping_returnsTheStandardEnvelopeWithOkStatus() throws Exception {
    Instant timestamp = Instant.parse("2026-01-01T12:00:00Z");
    given(pingUseCase.ping()).willReturn(PingStatus.ok(timestamp));

    mockMvc
        .perform(get("/ping"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.data.status").value("ok"))
        .andExpect(jsonPath("$.data.timestamp").value("2026-01-01T12:00:00Z"))
        .andExpect(
            jsonPath("$.meta.correlationId")
                .value(
                    matchesPattern(
                        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")));
  }

  @Test
  void ping_isReachableWithNoBearerToken() throws Exception {
    given(pingUseCase.ping()).willReturn(PingStatus.ok(Instant.now()));

    mockMvc.perform(get("/ping")).andExpect(status().isOk());
  }

  @Test
  void ping_isReachableWithAValidBearerToken() throws Exception {
    given(pingUseCase.ping()).willReturn(PingStatus.ok(Instant.now()));

    mockMvc.perform(get("/ping").with(jwt())).andExpect(status().isOk());
  }

  @Test
  void ping_echoesASuppliedCorrelationIdInTheEnvelope() throws Exception {
    given(pingUseCase.ping()).willReturn(PingStatus.ok(Instant.now()));
    String suppliedId = "11111111-2222-3333-4444-555555555555";

    mockMvc
        .perform(get("/ping").header("X-Correlation-Id", suppliedId))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Correlation-Id", suppliedId))
        .andExpect(jsonPath("$.meta.correlationId").value(suppliedId));
  }

  @Test
  void ping_generatesAValidUuidCorrelationIdWhenNoneIsSupplied() throws Exception {
    given(pingUseCase.ping()).willReturn(PingStatus.ok(Instant.now()));

    mockMvc
        .perform(get("/ping"))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    "X-Correlation-Id",
                    matchesPattern(
                        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")));
  }

  @Test
  void unknownRoute_returnsAProblemDetailWithNoInternalDetail() throws Exception {
    mockMvc
        .perform(get("/does-not-exist").with(jwt()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.correlationId").exists())
        .andExpect(jsonPath("$.detail", not(containsString("Exception"))));
  }

  @Test
  void protectedPath_withNoToken_returnsUnauthenticatedProblemDetail() throws Exception {
    mockMvc
        .perform(get("/does-not-exist"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.correlationId").exists());
  }

  @Test
  void protectedPath_withAMalformedBearerToken_returnsUnauthenticatedProblemDetail()
      throws Exception {
    mockMvc
        .perform(get("/does-not-exist").header("Authorization", "Bearer garbage.token.here"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.correlationId").exists());
  }

  @Test
  void ping_withANonUuidCorrelationIdHeader_isReplacedWithAGeneratedUuidNotRejected()
      throws Exception {
    given(pingUseCase.ping()).willReturn(PingStatus.ok(Instant.now()));

    mockMvc
        .perform(get("/ping").header("X-Correlation-Id", "not-a-uuid"))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    "X-Correlation-Id",
                    matchesPattern(
                        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")))
        .andExpect(
            jsonPath("$.meta.correlationId")
                .value(
                    matchesPattern(
                        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")));
  }

  @Test
  void unsupportedMethod_onAKnownRoute_returnsMethodNotAllowedProblemDetail() throws Exception {
    mockMvc
        .perform(post("/ping").with(jwt()))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
        .andExpect(jsonPath("$.correlationId").exists());
  }
}
