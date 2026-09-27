package com.acme.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.acme.testsupport.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * No write method succeeds anywhere, with no credentials and no CSRF token: {@code 405} on {@code
 * /ping} (Allow includes GET) and on the interface-description assets (Allow exactly {@code GET,
 * HEAD}); {@code 404} on a path the service does not offer. Always problem+json, never {@code 2xx},
 * {@code 401}, {@code 403} or {@code 5xx} (design D3/D4, uniform-responses "Service is read-only
 * and public").
 */
@AutoConfigureMockMvc
class ReadOnlyRefusalTest extends PostgresIntegrationTest {

  private static final HttpMethod[] WRITE_METHODS = {
    HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE
  };

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private static Stream<Arguments> writeMethods() {
    return Stream.of(WRITE_METHODS).map(Arguments::of);
  }

  private static Stream<Arguments> writesOnDescriptionAssets() {
    return Stream.of("/openapi/openapi.bundled.yaml", "/swagger-ui/index.html")
        .flatMap(path -> Stream.of(WRITE_METHODS).map(method -> Arguments.of(path, method)));
  }

  @ParameterizedTest
  @MethodSource("writeMethods")
  void writeOnPingIsMethodNotAllowedAndAllowsGet(HttpMethod method) throws Exception {
    MvcResult result = mockMvc.perform(request(method, "/ping")).andReturn();

    assertRefusal(result, 405, "METHOD_NOT_ALLOWED");
    assertThat(result.getResponse().getHeader("Allow")).contains("GET");
  }

  @ParameterizedTest
  @MethodSource("writesOnDescriptionAssets")
  void writeOnADescriptionAssetIsMethodNotAllowedWithGetAndHeadOnly(String path, HttpMethod method)
      throws Exception {
    MvcResult result = mockMvc.perform(request(method, path)).andReturn();

    assertRefusal(result, 405, "METHOD_NOT_ALLOWED");
    assertThat(result.getResponse().getHeader("Allow").replace(" ", "")).isEqualTo("GET,HEAD");
  }

  @ParameterizedTest
  @MethodSource("writeMethods")
  void writeOnAnUnknownPathIsNotFound(HttpMethod method) throws Exception {
    MvcResult result =
        mockMvc
            .perform(request(method, "/movies/123").contentType("application/json").content("{}"))
            .andReturn();

    assertRefusal(result, 404, "NOT_FOUND");
  }

  private void assertRefusal(MvcResult result, int status, String code) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
    assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(
            objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asText())
        .isEqualTo(code);
  }
}
