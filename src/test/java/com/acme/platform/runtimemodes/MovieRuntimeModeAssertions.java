package com.acme.platform.runtimemodes;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * The movie behaviours that must answer identically in standalone and persistent mode ({@code
 * catalog/movies} "Movie details behave identically in both runtime modes", design D6): {@code 404}
 * {@code NOT_FOUND} for a random well-formed identifier, and {@code 400} {@code BAD_REQUEST} for a
 * malformed one. Search ({@code catalog/movies} "Search behaves identically in both runtime
 * modes"): {@code 400} for an unknown genre, and {@code 200} with an empty list for a title that
 * matches nothing. Kept separate from {@link Uc000Assertions}, which stays unchanged.
 */
final class MovieRuntimeModeAssertions {

  private static final String CONTEXT_PATH = "/api/v1";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private MovieRuntimeModeAssertions() {}

  static void runAll(MockMvc mockMvc) throws Exception {
    unknownWellFormedIdIsNotFound(mockMvc);
    malformedIdIsBadRequest(mockMvc);
    unknownGenreIsBadRequest(mockMvc);
    titleMatchingNothingIsAnEmptySuccess(mockMvc);
  }

  private static void unknownGenreIsBadRequest(MockMvc mockMvc) throws Exception {
    assertProblem(
        mockMvc.perform(request("/movies").param("genre", "Spaghetti")).andReturn(),
        400,
        "BAD_REQUEST");
  }

  private static void titleMatchingNothingIsAnEmptySuccess(MockMvc mockMvc) throws Exception {
    MvcResult result =
        mockMvc.perform(request("/movies").param("title", "zzzz-no-such-title")).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(result.getResponse().getContentType()).startsWith("application/json");
    JsonNode body = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("data").get("_embedded").get("movies").isArray()).isTrue();
    assertThat(body.get("data").get("_embedded").get("movies").isEmpty()).isTrue();
    assertThat(body.get("meta").get("pagination").get("totalElements").asLong()).isZero();
  }

  private static void unknownWellFormedIdIsNotFound(MockMvc mockMvc) throws Exception {
    assertProblem(
        mockMvc.perform(request("/movies/" + UUID.randomUUID())).andReturn(), 404, "NOT_FOUND");
  }

  private static void malformedIdIsBadRequest(MockMvc mockMvc) throws Exception {
    assertProblem(
        mockMvc.perform(request("/movies/not-a-movie-id")).andReturn(), 400, "BAD_REQUEST");
  }

  private static void assertProblem(MvcResult result, int status, String code) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
    assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
    JsonNode body = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("code").asText()).isEqualTo(code);
  }

  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
      String path) {
    return MockMvcRequestBuilders.request(HttpMethod.GET, CONTEXT_PATH + path)
        .contextPath(CONTEXT_PATH);
  }
}
