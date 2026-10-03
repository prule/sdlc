package com.acme.platform.availability.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.platform.availability.application.port.in.CheckAvailabilityUseCase;
import com.acme.platform.availability.domain.model.Availability;
import com.acme.platform.availability.domain.model.AvailabilityStatus;
import com.acme.platform.web.PlatformWebTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@PlatformWebTest(controllers = PingController.class)
class PingControllerTest {

  private static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T00:00:00Z");

  @TestConfiguration
  static class FixedClockConfig {
    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    }
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private CheckAvailabilityUseCase checkAvailabilityUseCase;

  @Test
  void answersTheExactEnvelope() throws Exception {
    BDDMockito.given(checkAvailabilityUseCase.checkAvailability())
        .willReturn(new Availability(AvailabilityStatus.UP));

    MvcResult result =
        mockMvc
            .perform(get("/api/v1/ping").contextPath("/api/v1"))
            .andExpect(status().isOk())
            .andReturn();

    String correlationHeader = result.getResponse().getHeader("X-Correlation-Id");
    assertThat(correlationHeader).isNotBlank();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.fieldNames()).toIterable().containsExactlyInAnyOrder("data", "meta");

    JsonNode data = body.get("data");
    assertThat(data.fieldNames()).toIterable().containsExactlyInAnyOrder("status", "_links");
    assertThat(data.get("status").asText()).isEqualTo("UP");
    assertThat(data.get("_links").get("self").get("href").asText()).endsWith("/api/v1/ping");

    JsonNode meta = body.get("meta");
    assertThat(meta.get("timestamp").asText()).isEqualTo("2026-01-01T00:00:00Z");
    assertThat(meta.get("correlationId").asText()).isEqualTo(correlationHeader);
    // Only a paged list's meta carries pagination (uniform-responses "Uniform success envelope").
    assertThat(meta.has("pagination")).isFalse();
  }

  @Test
  void wellFormedInboundCorrelationIdIsHonouredInHeaderAndBody() throws Exception {
    BDDMockito.given(checkAvailabilityUseCase.checkAvailability())
        .willReturn(new Availability(AvailabilityStatus.UP));
    String inbound = "3f2b8c1e-8d4a-4c1e-9f0a-2b7d6e5c4a31";

    MvcResult result =
        mockMvc
            .perform(get("/api/v1/ping").contextPath("/api/v1").header("X-Correlation-Id", inbound))
            .andExpect(status().isOk())
            .andReturn();

    assertThat(result.getResponse().getHeader("X-Correlation-Id")).isEqualTo(inbound);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("meta").get("correlationId").asText()).isEqualTo(inbound);
  }

  @Test
  void malformedInboundCorrelationIdIsReplacedNotRejected() throws Exception {
    BDDMockito.given(checkAvailabilityUseCase.checkAvailability())
        .willReturn(new Availability(AvailabilityStatus.UP));

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/ping").contextPath("/api/v1").header("X-Correlation-Id", "not-a-uuid"))
            .andExpect(status().isOk())
            .andReturn();

    String header = result.getResponse().getHeader("X-Correlation-Id");
    assertThat(header).isNotEqualTo("not-a-uuid");
    assertThat(java.util.UUID.fromString(header)).isNotNull();
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("meta").get("correlationId").asText()).isEqualTo(header);
  }

  @Test
  void selfLinkHonoursForwardedHeaders() throws Exception {
    BDDMockito.given(checkAvailabilityUseCase.checkAvailability())
        .willReturn(new Availability(AvailabilityStatus.UP));

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/ping")
                    .contextPath("/api/v1")
                    .header("X-Forwarded-Proto", "https")
                    .header("X-Forwarded-Host", "api.example.test"))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("data").get("_links").get("self").get("href").asText())
        .isEqualTo("https://api.example.test/api/v1/ping");
  }
}
