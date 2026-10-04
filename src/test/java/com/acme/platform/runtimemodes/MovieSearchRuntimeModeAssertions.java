package com.acme.platform.runtimemodes;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * The movie search behaviours that must answer identically in standalone and persistent mode
 * ({@code catalog/movies} "Movie search responds in both runtime modes", design D6): {@code 200}
 * for browsing, and {@code 400} {@code BAD_REQUEST} naming {@code 'size'} and {@code 'genre'} for
 * the respective refusals. Kept separate from {@link MovieRuntimeModeAssertions}.
 */
final class MovieSearchRuntimeModeAssertions {

  private static final String CONTEXT_PATH = "/api/v1";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private MovieSearchRuntimeModeAssertions() {}

  static void runAll(MockMvc mockMvc) throws Exception {
    browseSucceeds(mockMvc);
    invalidSizeIsRefusedNamingSize(mockMvc);
    unknownGenreIsRefusedNamingGenre(mockMvc);
  }

  private static void browseSucceeds(MockMvc mockMvc) throws Exception {
    MvcResult result = mockMvc.perform(request("/movies")).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
  }

  private static void invalidSizeIsRefusedNamingSize(MockMvc mockMvc) throws Exception {
    assertProblem(
        mockMvc.perform(request("/movies?size=0")).andReturn(), 400, "BAD_REQUEST", "'size'");
  }

  private static void unknownGenreIsRefusedNamingGenre(MockMvc mockMvc) throws Exception {
    assertProblem(
        mockMvc.perform(request("/movies?genre=Western")).andReturn(),
        400,
        "BAD_REQUEST",
        "'genre'");
  }

  private static void assertProblem(
      MvcResult result, int status, String code, String detailContains) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
    assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
    JsonNode body = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("code").asText()).isEqualTo(code);
    assertThat(body.get("detail").asText()).contains(detailContains);
  }

  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
      String path) {
    return MockMvcRequestBuilders.request(HttpMethod.GET, CONTEXT_PATH + path)
        .contextPath(CONTEXT_PATH);
  }
}
