package com.acme.platform.interfacedescription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.acme.testsupport.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Actuator's HTTP surface (including its discovery root) and springdoc-style generated API-doc
 * endpoints are not reachable: there are no undocumented HTTP endpoints (design D5, task 6.5).
 */
@AutoConfigureMockMvc
class NoUndocumentedHttpCapabilitiesTest extends PostgresIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void actuatorDiscoveryRootIsNotExposed() throws Exception {
    assertNotFoundProblem(mockMvc.perform(get("/actuator")).andReturn());
  }

  @Test
  void actuatorHealthIsNotExposed() throws Exception {
    assertNotFoundProblem(mockMvc.perform(get("/actuator/health")).andReturn());
  }

  @Test
  void generatedApiDocsEndpointIsNotExposed() throws Exception {
    assertNotFoundProblem(mockMvc.perform(get("/v3/api-docs")).andReturn());
  }

  @Test
  void theErrorFallbackIsNotReachableAsAnEndpoint() throws Exception {
    assertNotFoundProblem(mockMvc.perform(get("/error")).andReturn());
  }

  private void assertNotFoundProblem(MvcResult result) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("code").asText()).isEqualTo("NOT_FOUND");
  }
}
