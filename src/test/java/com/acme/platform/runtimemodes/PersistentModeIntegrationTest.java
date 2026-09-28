package com.acme.platform.runtimemodes;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.testsupport.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Persistent mode (the {@code postgres} profile, against a real Testcontainers PostgreSQL instance)
 * serves the same UC-000 and movie behaviours as standalone mode (platform/runtime-modes, design
 * D8; {@code catalog/movies} "Movie details behave identically in both runtime modes", design D6).
 */
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
class PersistentModeIntegrationTest extends PostgresIntegrationTest {

  private static final String CONTEXT_PATH = "/api/v1";
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired private MockMvc mockMvc;

  @Test
  void persistentModeServesEveryUc000Behaviour() throws Exception {
    Uc000Assertions.runAll(mockMvc);
  }

  @Test
  void persistentModeServesEveryMovieBehaviour() throws Exception {
    MovieRuntimeModeAssertions.runAll(mockMvc);
  }

  /**
   * Secondary check only: the primary proof that persistent mode excludes the demo seed is {@code
   * FlywayLocationsConfigTest} (design D7), since this base class always pins {@code
   * spring.flyway.locations} regardless of profile.
   */
  @Test
  void theStandaloneSampleMovieIdDoesNotExistHere() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get(
                        CONTEXT_PATH + "/movies/11111111-1111-4111-8111-111111111111")
                    .contextPath(CONTEXT_PATH))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("code").asText()).isEqualTo("NOT_FOUND");
  }
}
