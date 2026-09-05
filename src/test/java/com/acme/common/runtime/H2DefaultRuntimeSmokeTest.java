package com.acme.common.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
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
 * runtime *configuration itself* (Flyway applies, the demo seed populates, the documented endpoints
 * respond — including the two native-SQL id-page paths, {@code MovieSearchPersistenceAdapter} and
 * {@code PersonFilmographyJpaAdapter}, that must be engine-portable), not persistence logic. All
 * DB/persistence-logic tests remain on Testcontainers-Postgres via {@link
 * com.acme.common.test.PostgresIntegrationTest}; see standards/testing.md for the (deliberately
 * narrow) carve-out this test represents.
 *
 * <p>Uses {@link WebEnvironment#RANDOM_PORT} with a real HTTP client ({@link TestRestTemplate}),
 * not MockMvc: MockMvc does not apply {@code server.servlet.context-path: /api/v1}, so a MockMvc
 * request to {@code /movies} would 404 without actually proving the real HTTP contract. {@link
 * TestRestTemplate}'s root URI DOES honour {@code server.servlet.context-path} (it is built from
 * {@code ServerProperties}), so requests below use paths relative to {@code /api/v1} (e.g. {@code
 * /movies}); the effective request is {@code /api/v1/movies}, not a bare {@code /movies}.
 *
 * <p>Movie and person ids are computed with the same deterministic derivation as {@code
 * DemoMovieSeedLoader} ({@code UUID.nameUUIDFromBytes(title.getBytes())} for movies, {@code
 * UUID.nameUUIDFromBytes(("person-" + name).getBytes(UTF_8))} for people) against the committed
 * {@code demo-data/movies.json} dataset, so the ids are known without querying for them first.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class H2DefaultRuntimeSmokeTest {

  private static final UUID THE_MATRIX_ID = UUID.nameUUIDFromBytes("The Matrix".getBytes());
  private static final UUID KEANU_REEVES_ID =
      UUID.nameUUIDFromBytes("person-Keanu Reeves".getBytes(StandardCharsets.UTF_8));

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void flywayAppliesAllMigrationsOnH2() {
    List<String> appliedVersions =
        jdbcTemplate.queryForList(
            "SELECT \"version\" FROM \"flyway_schema_history\" WHERE \"success\" = true ORDER BY"
                + " \"installed_rank\"",
            String.class);

    assertThat(appliedVersions)
        .as("Flyway migrations applied to the H2 default datasource")
        .contains("1", "2", "3", "4", "5");
  }

  @Test
  void moviesSearchEndpointRespondsPopulatedOnDefaultH2Runtime() throws Exception {
    ResponseEntity<String> response = restTemplate.getForEntity("/movies", String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    JsonNode body = objectMapper.readTree(response.getBody());
    JsonNode movies = body.path("data").path("_embedded").path("movies");
    assertThat(movies.isArray()).isTrue();
    assertThat(movies).isNotEmpty();
  }

  @Test
  void peopleEndpointRespondsPopulatedOnDefaultH2Runtime() throws Exception {
    ResponseEntity<String> response = restTemplate.getForEntity("/people", String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    JsonNode body = objectMapper.readTree(response.getBody());
    JsonNode people = body.path("data").path("_embedded").path("people");
    assertThat(people.isArray()).isTrue();
    assertThat(people).isNotEmpty();
  }

  @Test
  void movieDetailEndpointRespondsPopulatedOnDefaultH2Runtime() throws Exception {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/movies/{id}", String.class, THE_MATRIX_ID);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    JsonNode data = objectMapper.readTree(response.getBody()).path("data");
    assertThat(data.path("title").asText()).isEqualTo("The Matrix");
  }

  @Test
  void movieCreditsEndpointRespondsPopulatedOnDefaultH2Runtime() throws Exception {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/movies/{id}/credits", String.class, THE_MATRIX_ID);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    JsonNode embedded = objectMapper.readTree(response.getBody()).path("data").path("_embedded");
    assertThat(embedded.path("cast")).isNotEmpty();
    assertThat(embedded.path("crew")).isNotEmpty();
  }

  /**
   * Exercises {@code PersonFilmographyJpaAdapter}'s native id-page query, the direct source of the
   * H2 {@code byte[]}-vs-{@code UUID} portability bug this change fixes.
   */
  @Test
  void personFilmographyEndpointRespondsPopulatedOnDefaultH2Runtime() throws Exception {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/people/{id}/credits", String.class, KEANU_REEVES_ID);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    JsonNode filmography =
        objectMapper
            .readTree(response.getBody())
            .path("data")
            .path("_embedded")
            .path("filmography");
    assertThat(filmography.isArray()).isTrue();
    assertThat(filmography).isNotEmpty();
  }
}
