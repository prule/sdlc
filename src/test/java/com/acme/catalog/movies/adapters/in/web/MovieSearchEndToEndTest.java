package com.acme.catalog.movies.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.acme.testsupport.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end coverage for {@code GET /api/v1/movies} against real PostgreSQL, exercising every
 * {@code catalog/movies} and {@code platform/collection-paging} scenario that needs real data
 * (design D5/D6, task 7.2).
 */
@AutoConfigureMockMvc
class MovieSearchEndToEndTest extends PostgresIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update("DELETE FROM movie_genre");
    jdbcTemplate.update("DELETE FROM movie");
    jdbcTemplate.update("DELETE FROM genre");
  }

  private UUID insertGenre(String name) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update("INSERT INTO genre (id, name) VALUES (?, ?)", id, name);
    return id;
  }

  private UUID insertMovie(String title, int releaseYear, BigDecimal rating) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO movie (id, title, release_year, rating) VALUES (?, ?, ?, ?)",
        id,
        title,
        releaseYear,
        rating);
    return id;
  }

  private void linkGenre(UUID movieId, UUID genreId) {
    jdbcTemplate.update(
        "INSERT INTO movie_genre (movie_id, genre_id) VALUES (?, ?)", movieId, genreId);
  }

  /** Sends {@code query} as written (a URI, not a template), so escapes are never re-encoded. */
  private JsonNode search(String query) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get(URI.create("/api/v1/movies" + query)).contextPath("/api/v1"))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  @Test
  void browseTheWholeCatalog() throws Exception {
    insertMovie("A", 2000, null);
    insertMovie("B", 2001, null);
    insertMovie("C", 2002, null);

    JsonNode body = search("");

    assertThat(body.get("data").get("_embedded").get("movies")).hasSize(3);
    assertThat(body.get("meta").get("pagination").get("totalElements").asLong()).isEqualTo(3);
  }

  @Test
  void anEmptyCatalogIsASuccessWithAnEmptyPage() throws Exception {
    JsonNode body = search("");

    assertThat(body.get("data").get("_embedded").get("movies")).isEmpty();
    assertThat(body.get("meta").get("pagination").get("totalElements").asLong()).isEqualTo(0);
  }

  @Test
  void noMatchesIsASuccessWithAnEmptyPage() throws Exception {
    insertMovie("Arrival", 2016, null);

    JsonNode body = search("?title=nomatchatall");

    assertThat(body.get("data").get("_embedded").get("movies")).isEmpty();
    assertThat(body.get("meta").get("pagination").get("totalElements").asLong()).isEqualTo(0);
  }

  @Test
  void anEmptyTitleBrowsesTheWholeCatalog() throws Exception {
    insertMovie("The Grand Heist", 2005, null);
    insertMovie("Heist Night", 2010, null);
    insertMovie("Arrival", 2016, null);

    JsonNode body = search("?title=");

    assertThat(body.get("data").get("_embedded").get("movies"))
        .extracting(movie -> movie.get("title").asText())
        .containsExactlyInAnyOrder("The Grand Heist", "Heist Night", "Arrival");
    assertThat(body.get("meta").get("pagination").get("totalElements").asLong()).isEqualTo(3);
  }

  @Test
  void aWhitespaceOnlyTitleBrowsesTheWholeCatalog() throws Exception {
    insertMovie("The Grand Heist", 2005, null);
    insertMovie("Heist Night", 2010, null);
    insertMovie("Arrival", 2016, null);

    JsonNode body = search("?title=%20%20");

    assertThat(body.get("data").get("_embedded").get("movies"))
        .extracting(movie -> movie.get("title").asText())
        .containsExactlyInAnyOrder("The Grand Heist", "Heist Night", "Arrival");
  }

  @Test
  void titleGenreYearAndRatingFiltersCombineAndOrderIsApplied() throws Exception {
    UUID thriller = insertGenre("Thriller");
    UUID meetsAll = insertMovie("Night Terror", 2005, new BigDecimal("4.0"));
    linkGenre(meetsAll, thriller);
    insertMovie("Day Out", 2005, new BigDecimal("4.0"));

    JsonNode body =
        search("?title=night&genre=Thriller&releaseYearFrom=2000&minRating=3&sort=title");

    JsonNode movies = body.get("data").get("_embedded").get("movies");
    assertThat(movies).hasSize(1);
    assertThat(movies.get(0).get("title").asText()).isEqualTo("Night Terror");
  }

  @Test
  void middleFirstLastAndBeyondLastPageLinks() throws Exception {
    for (int i = 0; i < 25; i++) {
      insertMovie("Movie " + i, 2000 + i, null);
    }

    JsonNode first = search("?size=7");
    JsonNode middle = search("?size=7&page=1");
    JsonNode last = search("?size=7&page=3");
    JsonNode beyond = search("?size=7&page=4");

    assertThat(first.get("data").get("_links").has("prev")).isFalse();
    assertThat(first.get("data").get("_links").has("next")).isTrue();

    assertThat(middle.get("data").get("_links").has("prev")).isTrue();
    assertThat(middle.get("data").get("_links").has("next")).isTrue();

    assertThat(last.get("data").get("_embedded").get("movies")).hasSize(4);
    assertThat(last.get("data").get("_links").has("next")).isFalse();

    assertThat(beyond.get("data").get("_embedded").get("movies")).isEmpty();
    assertThat(beyond.get("meta").get("pagination").get("totalElements").asLong()).isEqualTo(25);
  }

  @Test
  void followingASummarysSelfLinkReachesTheSameMoviesDetails() throws Exception {
    UUID movieId = insertMovie("Arrival", 2016, new BigDecimal("4.5"));

    JsonNode searchBody = search("");
    String href =
        searchBody
            .get("data")
            .get("_embedded")
            .get("movies")
            .get(0)
            .get("_links")
            .get("self")
            .get("href")
            .asText();
    String path = href.substring(href.indexOf("/movies/"));

    MvcResult detailsResult =
        mockMvc.perform(get("/api/v1" + path).contextPath("/api/v1")).andReturn();

    assertThat(detailsResult.getResponse().getStatus()).isEqualTo(200);
    JsonNode details = objectMapper.readTree(detailsResult.getResponse().getContentAsString());
    assertThat(details.get("data").get("id").asText()).isEqualTo(movieId.toString());
  }

  @Test
  void summariesCarryExactlyTheirRecordedMembersAndLinkToTheirDetails() throws Exception {
    UUID drama = insertGenre("Drama");
    UUID sciFi = insertGenre("Sci-Fi");
    UUID curated = UUID.randomUUID();
    insertFullMovie(curated, "Arrival", 2016, 116, "A linguist is recruited.", "4.5");
    linkGenre(curated, sciFi);
    linkGenre(curated, drama);
    UUID fiveRated = UUID.randomUUID();
    insertFullMovie(fiveRated, "Five Rated", 2002, null, null, "5.0");
    UUID minimal = UUID.randomUUID();
    insertFullMovie(minimal, "Untitled Reel", 1974, null, null, null);

    // Default order is release year descending: curated, fiveRated, minimal.
    JsonNode movies = search("").get("data").get("_embedded").get("movies");
    JsonNode curatedSummary = movies.get(0);
    JsonNode fiveRatedSummary = movies.get(1);
    JsonNode minimalSummary = movies.get(2);

    assertThat(curatedSummary.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder(
            "id", "title", "releaseYear", "genres", "runtimeMinutes", "rating", "_links");
    assertThat(curatedSummary.get("id").asText()).isEqualTo(curated.toString());
    assertThat(curatedSummary.get("genres"))
        .map(JsonNode::asText)
        .containsExactly("Drama", "Sci-Fi");
    assertThat(fiveRatedSummary.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder("id", "title", "releaseYear", "genres", "rating", "_links");
    assertThat(fiveRatedSummary.get("rating").toString()).isEqualTo("5");
    assertThat(minimalSummary.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder("id", "title", "releaseYear", "genres", "_links");
    assertThat(minimalSummary.get("genres").isArray()).isTrue();
    assertThat(minimalSummary.get("genres").isEmpty()).isTrue();
    assertThat(List.of(curatedSummary, fiveRatedSummary, minimalSummary))
        .extracting(this::followSelfLink)
        .containsExactly(
            new FollowedLink(200, curated.toString()),
            new FollowedLink(200, fiveRated.toString()),
            new FollowedLink(200, minimal.toString()));
  }

  private void insertFullMovie(
      UUID id,
      String title,
      int releaseYear,
      Integer runtimeMinutes,
      String synopsis,
      String rating) {
    jdbcTemplate.update(
        "INSERT INTO movie (id, title, release_year, runtime_minutes, synopsis, rating) "
            + "VALUES (?, ?, ?, ?, ?, ?)",
        id,
        title,
        releaseYear,
        runtimeMinutes,
        synopsis,
        rating == null ? null : new BigDecimal(rating));
  }

  /** The status and {@code data.id} of the details reached through a summary's self link. */
  private FollowedLink followSelfLink(JsonNode summary) {
    String href = summary.get("_links").get("self").get("href").asText();
    String path = href.substring(href.indexOf("/movies/"));
    try {
      MvcResult result = mockMvc.perform(get("/api/v1" + path).contextPath("/api/v1")).andReturn();
      JsonNode details = objectMapper.readTree(result.getResponse().getContentAsString());
      return new FollowedLink(
          result.getResponse().getStatus(), details.get("data").get("id").asText());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private record FollowedLink(int status, String id) {}

  @Test
  void aPostFollowedByAGetLeavesTotalElementsUnchanged() throws Exception {
    insertMovie("Arrival", 2016, null);

    MvcResult postResult =
        mockMvc
            .perform(
                post("/api/v1/movies")
                    .contextPath("/api/v1")
                    .contentType("application/json")
                    .content("{}"))
            .andReturn();
    assertThat(postResult.getResponse().getStatus()).isEqualTo(405);

    JsonNode body = search("");
    assertThat(body.get("meta").get("pagination").get("totalElements").asLong()).isEqualTo(1);
  }
}
