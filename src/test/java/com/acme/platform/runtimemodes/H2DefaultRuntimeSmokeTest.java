package com.acme.platform.runtimemodes;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * The single H2-booting test {@code standards/testing.md} permits: it proves the H2 default runtime
 * configuration (no profile active) works, not persistence logic. Standalone mode needs no external
 * infrastructure (platform/runtime-modes). Also the permitted place to assert the demo seed
 * populates ({@code catalog/movies} "Standalone mode offers sample movies", design D7).
 */
@SpringBootTest
@AutoConfigureMockMvc
class H2DefaultRuntimeSmokeTest {

  private static final String CONTEXT_PATH = "/api/v1";
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired private MockMvc mockMvc;

  @Test
  void standaloneModeServesEveryUc000Behaviour() throws Exception {
    Uc000Assertions.runAll(mockMvc);
  }

  @Test
  void standaloneModeServesEveryMovieBehaviour() throws Exception {
    MovieRuntimeModeAssertions.runAll(mockMvc);
  }

  @Test
  void fullyCuratedSampleMovieIsServed() throws Exception {
    JsonNode data = getMovie("11111111-1111-4111-8111-111111111111");

    assertThat(data.has("runtimeMinutes")).isTrue();
    assertThat(data.has("synopsis")).isTrue();
    assertThat(data.has("rating")).isTrue();
    // Seeded out of A-Z order (Sci-Fi linked before Drama), so this also proves BR-4 ordering.
    assertThat(data.get("genres")).map(JsonNode::asText).containsExactly("Drama", "Sci-Fi");
  }

  @Test
  void minimalSampleMovieIsServed() throws Exception {
    JsonNode data = getMovie("22222222-2222-4222-8222-222222222222");

    assertThat(data.get("genres").isEmpty()).isTrue();
    assertThat(data.has("runtimeMinutes")).isFalse();
    assertThat(data.has("synopsis")).isFalse();
    assertThat(data.has("rating")).isFalse();
  }

  @Test
  void browsingListsTheSampleMoviesNewestFirst() throws Exception {
    MvcResult result =
        mockMvc
            .perform(MockMvcRequestBuilders.get(CONTEXT_PATH + "/movies").contextPath(CONTEXT_PATH))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.at("/data/_embedded/movies"))
        .map(movie -> movie.get("title").asText())
        .containsExactly("Arrival", "The Grand Heist", "Laugh Track", "Untitled Reel");
    assertThat(body.at("/meta/pagination/totalElements").asLong()).isEqualTo(4);
  }

  private JsonNode getMovie(String id) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get(CONTEXT_PATH + "/movies/" + id)
                    .contextPath(CONTEXT_PATH))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
  }
}
