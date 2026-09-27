package com.acme.platform.runtimemodes;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * The shared UC-000 behaviours (availability check, both description forms, not-found,
 * method-not-allowed, not-acceptable) that must answer identically in standalone and persistent
 * mode: same status, same {@code Content-Type}, same body structure. Only {@code meta.timestamp}
 * and correlation ids may differ (design D8, platform/runtime-modes "Identical behaviour").
 *
 * <p>Requests are built with the {@code /api/v1} context path explicitly (matching {@code
 * server.servlet.context-path}) so the self-link assertion reflects the same request shape a real
 * deployment sees; MockMvc's mock servlet context does not apply it on its own.
 */
final class Uc000Assertions {

  private static final String CONTEXT_PATH = "/api/v1";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private Uc000Assertions() {}

  static void runAll(MockMvc mockMvc) throws Exception {
    availabilityCheckAnswersUp(mockMvc);
    toolReadableDescriptionIsServed(mockMvc);
    browsableDescriptionIsServed(mockMvc);
    unknownPathIsNotFound(mockMvc);
    writeToPingIsMethodNotAllowed(mockMvc);
    unsatisfiableAcceptOnPingIsNotAcceptable(mockMvc);
  }

  private static void availabilityCheckAnswersUp(MockMvc mockMvc) throws Exception {
    MvcResult result = mockMvc.perform(request(HttpMethod.GET, "/ping")).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(result.getResponse().getContentType()).startsWith("application/json");
    JsonNode body = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
    assertThat(body.at("/data/status").asText()).isEqualTo("UP");
    String selfHref = body.at("/data/_links/self/href").asText();
    assertThat(selfHref).endsWith("/api/v1/ping");

    MvcResult followed =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get(java.net.URI.create(selfHref)).contextPath(CONTEXT_PATH))
            .andReturn();
    assertThat(followed.getResponse().getStatus()).isEqualTo(200);
    assertThat(
            OBJECT_MAPPER
                .readTree(followed.getResponse().getContentAsString())
                .at("/data/status")
                .asText())
        .isEqualTo("UP");
  }

  private static void toolReadableDescriptionIsServed(MockMvc mockMvc) throws Exception {
    MvcResult result =
        mockMvc.perform(request(HttpMethod.GET, "/openapi/openapi.bundled.yaml")).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(result.getResponse().getContentAsString()).contains("/ping");
  }

  private static void browsableDescriptionIsServed(MockMvc mockMvc) throws Exception {
    MvcResult result =
        mockMvc.perform(request(HttpMethod.GET, "/swagger-ui/index.html")).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
  }

  private static void unknownPathIsNotFound(MockMvc mockMvc) throws Exception {
    assertProblem(
        mockMvc.perform(request(HttpMethod.GET, "/no-such-thing")).andReturn(), 404, "NOT_FOUND");
    assertProblem(
        mockMvc.perform(request(HttpMethod.PUT, "/no-such-thing")).andReturn(), 404, "NOT_FOUND");
  }

  private static void writeToPingIsMethodNotAllowed(MockMvc mockMvc) throws Exception {
    assertProblem(
        mockMvc.perform(request(HttpMethod.POST, "/ping")).andReturn(), 405, "METHOD_NOT_ALLOWED");
  }

  private static void unsatisfiableAcceptOnPingIsNotAcceptable(MockMvc mockMvc) throws Exception {
    assertProblem(
        mockMvc
            .perform(request(HttpMethod.GET, "/ping").accept(MediaType.APPLICATION_XML))
            .andReturn(),
        406,
        "NOT_ACCEPTABLE");
  }

  private static void assertProblem(MvcResult result, int status, String code) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
    assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
    JsonNode body = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("code").asText()).isEqualTo(code);
  }

  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
      HttpMethod method, String path) {
    return MockMvcRequestBuilders.request(method, CONTEXT_PATH + path).contextPath(CONTEXT_PATH);
  }
}
