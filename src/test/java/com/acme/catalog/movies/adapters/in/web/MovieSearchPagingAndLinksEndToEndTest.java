package com.acme.catalog.movies.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.acme.testsupport.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Paging defaults and criteria-preserving navigation links on the real {@code GET /api/v1/movies}
 * against PostgreSQL (design D5): a 25-movie catalog where every movie carries Drama and Sci-Fi.
 */
@AutoConfigureMockMvc
class MovieSearchPagingAndLinksEndToEndTest extends PostgresIntegrationTest {

  private static final int CATALOG_SIZE = 25;
  private static final int MAX_PAGES = 10;
  private static final String CRITERIA_QUERY =
      "?title=Movie&genre=drama&genre=SCI-FI&foo=bar&sort=-rating&size=7&page=1";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void insertCatalog() {
    insertMoviesCarryingDramaAndSciFi(CATALOG_SIZE);
  }

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update("DELETE FROM movie_genre");
    jdbcTemplate.update("DELETE FROM movie");
    jdbcTemplate.update("DELETE FROM genre");
  }

  /** Fixture builder: {@code count} movies titled {@code Movie NN}, each with Drama and Sci-Fi. */
  private void insertMoviesCarryingDramaAndSciFi(int count) {
    UUID drama = insertGenre("Drama");
    UUID sciFi = insertGenre("Sci-Fi");
    for (int i = 0; i < count; i++) {
      UUID movieId = UUID.randomUUID();
      jdbcTemplate.update(
          "INSERT INTO movie (id, title, release_year) VALUES (?, ?, ?)",
          movieId,
          "Movie %02d".formatted(i),
          2000 + i);
      linkGenre(movieId, drama);
      linkGenre(movieId, sciFi);
    }
  }

  private UUID insertGenre(String name) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update("INSERT INTO genre (id, name) VALUES (?, ?)", id, name);
    return id;
  }

  private void linkGenre(UUID movieId, UUID genreId) {
    jdbcTemplate.update(
        "INSERT INTO movie_genre (movie_id, genre_id) VALUES (?, ?)", movieId, genreId);
  }

  private JsonNode search(String query) throws Exception {
    return search(request(query));
  }

  private JsonNode search(MockHttpServletRequestBuilder request) throws Exception {
    MvcResult result = mockMvc.perform(request).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  /** A URI (not a template), so the query is sent exactly as written. */
  private static MockHttpServletRequestBuilder request(String query) {
    return get(URI.create("/api/v1/movies" + query)).contextPath("/api/v1");
  }

  private static JsonNode movies(JsonNode body) {
    return body.get("data").get("_embedded").get("movies");
  }

  private static List<String> idsOf(JsonNode body) {
    return StreamSupport.stream(movies(body).spliterator(), false)
        .map(movie -> movie.get("id").asText())
        .toList();
  }

  @Test
  void theDefaultPageHoldsTwentyMovies() throws Exception {
    assertThat(movies(search(""))).hasSize(20);
  }

  @Test
  void theSecondDefaultPageHoldsTheRemainingFive() throws Exception {
    assertThat(movies(search("?page=1"))).hasSize(5);
  }

  @Test
  void anExplicitSizeIsHonoured() throws Exception {
    assertThat(movies(search("?size=7"))).hasSize(7);
  }

  @Test
  void walkingEveryPageOfSevenEqualsOneLargePageInOrder() throws Exception {
    List<String> walked = walkAllIds(7);
    List<String> onePage = idsOf(search("?size=100"));

    assertThat(onePage).hasSize(CATALOG_SIZE);
    assertThat(walked).isEqualTo(onePage);
  }

  /**
   * Walks every page (iteration is the behaviour under test, {@code standards/testing.md} §4): the
   * page count is read from page 0 and bounded by {@link #MAX_PAGES}, and the loop body only
   * collects.
   */
  private List<String> walkAllIds(int size) throws Exception {
    int totalPages =
        search("?size=" + size).get("meta").get("pagination").get("totalPages").asInt();
    assertThat(totalPages).isLessThanOrEqualTo(MAX_PAGES);

    List<String> ids = new ArrayList<>();
    for (int page = 0; page < totalPages; page++) {
      ids.addAll(idsOf(search("?size=" + size + "&page=" + page)));
    }
    return ids;
  }

  @Test
  void theLargestPageNumberIsAnEmptySuccess() throws Exception {
    JsonNode body = search("?page=2147483647&size=100");

    assertThat(movies(body)).isEmpty();
  }

  static Stream<Arguments> linkTargets() {
    return Stream.of(
        Arguments.of("self", 1),
        Arguments.of("first", 0),
        Arguments.of("prev", 0),
        Arguments.of("last", 3),
        Arguments.of("next", 2));
  }

  @ParameterizedTest
  @MethodSource("linkTargets")
  void everyLinkEchoesTheRecognisedParametersExactlyWithItsTargetPage(
      String relation, int targetPage) throws Exception {
    JsonNode links = search(CRITERIA_QUERY).get("data").get("_links");

    String href = links.get(relation).get("href").asText();

    assertThat(decodedQueryPairs(href))
        .containsExactly(
            "title=Movie",
            "genre=drama",
            "genre=SCI-FI",
            "sort=-rating",
            "size=7",
            "page=" + targetPage);
  }

  private static List<String> decodedQueryPairs(String href) {
    String rawQuery = URI.create(href).getRawQuery();
    return Arrays.stream(rawQuery.split("&"))
        .map(pair -> URLDecoder.decode(pair, StandardCharsets.UTF_8))
        .toList();
  }

  @Test
  void everyLinkHonoursForwardedProtoAndHost() throws Exception {
    JsonNode links =
        search(
                request("")
                    .header("X-Forwarded-Proto", "https")
                    .header("X-Forwarded-Host", "api.example.test"))
            .get("data")
            .get("_links");

    List<String> hrefs =
        StreamSupport.stream(links.spliterator(), false)
            .map(link -> link.get("href").asText())
            .toList();

    assertThat(hrefs)
        .isNotEmpty()
        .allSatisfy(href -> assertThat(href).startsWith("https://api.example.test/api/v1/movies?"));
  }
}
