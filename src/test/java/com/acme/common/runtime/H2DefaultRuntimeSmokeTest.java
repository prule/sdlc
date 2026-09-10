package com.acme.common.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the no-profile default runtime boots with zero external dependencies: no Testcontainers,
 * no {@code test}/{@code postgres} profile active, so the application context resolves its
 * datasource to the in-memory H2 engine exactly as {@code ./gradlew bootRun} would.
 *
 * <p>Intentionally the ONLY test in this codebase that exercises H2 — it verifies the H2 default
 * runtime *configuration itself* (Flyway applies, the documented platform endpoints respond,
 * Swagger UI serves the authored contract), not persistence logic. All DB/persistence-logic tests
 * remain on Testcontainers-Postgres via {@link com.acme.common.test.PostgresIntegrationTest}; see
 * standards/testing.md for the (deliberately narrow) carve-out this test represents.
 *
 * <p>Uses {@link WebEnvironment#RANDOM_PORT} with a real HTTP client ({@link TestRestTemplate}),
 * not MockMvc: MockMvc does not apply {@code server.servlet.context-path: /api/v1}, so a MockMvc
 * request to {@code /samples} would 404 without proving the real HTTP contract. {@link
 * TestRestTemplate}'s root URI DOES honour {@code server.servlet.context-path}, so requests below
 * use paths relative to {@code /api/v1} (e.g. {@code /samples}); the effective request is {@code
 * /api/v1/samples}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class H2DefaultRuntimeSmokeTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void flywayAppliesTheBaselineMigrationOnH2() {
    List<String> appliedVersions =
        jdbcTemplate.queryForList(
            "SELECT \"version\" FROM \"flyway_schema_history\" WHERE \"success\" = true ORDER BY"
                + " \"installed_rank\"",
            String.class);

    assertThat(appliedVersions)
        .as("Flyway migrations applied to the H2 default datasource")
        .contains("1");
  }

  @Test
  void pingEndpointRespondsOnDefaultH2Runtime() {
    ResponseEntity<String> response = restTemplate.getForEntity("/ping", String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
  }

  @Test
  void samplesEndpointRespondsOnDefaultH2Runtime() throws Exception {
    ResponseEntity<String> response = restTemplate.getForEntity("/samples", String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("data").path("_embedded").path("samples").isArray()).isTrue();
  }

  @Test
  void movieEndpointRespondsOnDefaultH2Runtime() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/movies/11111111-1111-1111-1111-111111111111", String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
  }

  @Test
  void movieCreditsEndpointRespondsOnDefaultH2Runtime() {
    ResponseEntity<String> response =
        restTemplate.getForEntity(
            "/movies/11111111-1111-1111-1111-111111111111/credits", String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
  }

  @Test
  void personEndpointRespondsOnDefaultH2Runtime() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/people/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
  }

  @Test
  void swaggerUiIsServedPubliclyOnDefaultH2Runtime() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/swagger-ui/index.html", String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
  }

  @Test
  void swaggerUiWebjarAssetIsServedPubliclyOnDefaultH2Runtime() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/webjars/swagger-ui/5.18.2/swagger-ui-bundle.js", String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
  }

  @Test
  void bundledOpenApiSpecIsServedPubliclyAndIsTheAuthoredContract() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/openapi/openapi.bundled.yaml", String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).contains("listSamples");
    assertThat(response.getBody()).contains("/samples");
  }

  @Test
  void noAnnotationGeneratedSpecIsExposedBecauseThereIsNoSpringdoc() {
    ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);

    assertThat(response.getStatusCode().value()).isNotEqualTo(200);
  }

  @Test
  void docsCarveOutDoesNotWidenTheAuthenticatedApiSurface() throws Exception {
    ResponseEntity<String> response = restTemplate.getForEntity("/__not-an-endpoint", String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(401);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("status").asInt()).isEqualTo(401);
  }
}
