package com.acme.catalog.movies.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.acme.testsupport.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end coverage for {@code GET /api/v1/movies/{id}} against real PostgreSQL, exercising every
 * {@code catalog/movies} scenario that needs real data (design D5/D6).
 */
@AutoConfigureMockMvc
class MovieEndToEndTest extends PostgresIntegrationTest {

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

  private void insertMovie(
      UUID id,
      String title,
      int releaseYear,
      Integer runtimeMinutes,
      String synopsis,
      BigDecimal rating) {
    jdbcTemplate.update(
        "INSERT INTO movie (id, title, release_year, runtime_minutes, synopsis, rating) "
            + "VALUES (?, ?, ?, ?, ?, ?)",
        id,
        title,
        releaseYear,
        runtimeMinutes,
        synopsis,
        rating);
  }

  private void linkGenre(UUID movieId, UUID genreId) {
    jdbcTemplate.update(
        "INSERT INTO movie_genre (movie_id, genre_id) VALUES (?, ?)", movieId, genreId);
  }

  private JsonNode getMovie(UUID id) throws Exception {
    MvcResult result =
        mockMvc.perform(get("/api/v1/movies/{id}", id).contextPath("/api/v1")).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  @Test
  void fullyPopulatedMovieHasExactlyTheExpectedMembers() throws Exception {
    UUID movieId = UUID.randomUUID();
    UUID drama = insertGenre("Drama");
    UUID sciFi = insertGenre("Sci-Fi");
    insertMovie(movieId, "Arrival", 2016, 116, "A linguist is recruited.", new BigDecimal("4.5"));
    linkGenre(movieId, sciFi);
    linkGenre(movieId, drama);

    JsonNode body = getMovie(movieId);
    JsonNode data = body.get("data");

    assertThat(data.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder(
            "id",
            "title",
            "releaseYear",
            "genres",
            "runtimeMinutes",
            "synopsis",
            "rating",
            "_links");
    assertThat(data.get("id").asText()).isEqualTo(movieId.toString());
    assertThat(data.get("title").asText()).isEqualTo("Arrival");
    assertThat(data.get("releaseYear").asInt()).isEqualTo(2016);
    assertThat(data.get("genres")).map(JsonNode::asText).containsExactly("Drama", "Sci-Fi");
    assertThat(data.get("runtimeMinutes").asInt()).isEqualTo(116);
    assertThat(data.get("synopsis").asText()).isEqualTo("A linguist is recruited.");
    assertThat(data.get("rating").asText()).isEqualTo("4.5");
    assertThat(data.get("_links").get("self").get("href").asText())
        .endsWith("/api/v1/movies/" + movieId);
  }

  @Test
  void genreOrderIsStableAcrossTwoRequests() throws Exception {
    UUID movieId = UUID.randomUUID();
    UUID thriller = insertGenre("Thriller");
    UUID drama = insertGenre("Drama");
    UUID sciFi = insertGenre("Sci-Fi");
    insertMovie(movieId, "Untitled", 2000, null, null, null);
    linkGenre(movieId, thriller);
    linkGenre(movieId, drama);
    linkGenre(movieId, sciFi);

    JsonNode first = getMovie(movieId).get("data").get("genres");
    JsonNode second = getMovie(movieId).get("data").get("genres");

    assertThat(first).map(JsonNode::asText).containsExactly("Drama", "Sci-Fi", "Thriller");
    assertThat(second).map(JsonNode::asText).containsExactly("Drama", "Sci-Fi", "Thriller");
  }

  @Test
  void movieWithNoGenresHasAnEmptyGenresArray() throws Exception {
    UUID movieId = UUID.randomUUID();
    insertMovie(movieId, "Untitled Reel", 1974, null, null, null);

    JsonNode data = getMovie(movieId).get("data");

    assertThat(data.get("genres").isEmpty()).isTrue();
  }

  @Test
  void ratingLiteralsAtTheBoundariesHaveNoTrailingZero() throws Exception {
    UUID lowMovie = UUID.randomUUID();
    UUID highMovie = UUID.randomUUID();
    insertMovie(lowMovie, "Zero Rated", 2001, null, null, new BigDecimal("0.0"));
    insertMovie(highMovie, "Five Rated", 2002, null, null, new BigDecimal("5.0"));

    assertThat(getMovie(lowMovie).get("data").get("rating").asText()).isEqualTo("0");
    assertThat(getMovie(highMovie).get("data").get("rating").asText()).isEqualTo("5");
  }

  @Test
  void absentOptionalFieldsAreOmittedEntirely() throws Exception {
    UUID movieId = UUID.randomUUID();
    insertMovie(movieId, "Untitled Reel", 1974, null, null, null);

    JsonNode data = getMovie(movieId).get("data");

    assertThat(data.has("runtimeMinutes")).isFalse();
    assertThat(data.has("synopsis")).isFalse();
    assertThat(data.has("rating")).isFalse();
  }

  @Test
  void unknownIdIsNotFound() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/movies/{id}", UUID.randomUUID()).contextPath("/api/v1"))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("code").asText()).isEqualTo("NOT_FOUND");
  }

  @Test
  void aPutFollowedByAGetLeavesTheMovieUnchanged() throws Exception {
    UUID movieId = UUID.randomUUID();
    insertMovie(movieId, "Arrival", 2016, 116, "A linguist is recruited.", new BigDecimal("4.5"));

    JsonNode before = getMovie(movieId).get("data");

    MvcResult putResult =
        mockMvc
            .perform(
                put("/api/v1/movies/{id}", movieId)
                    .contextPath("/api/v1")
                    .contentType("application/json")
                    .content("{}"))
            .andReturn();
    assertThat(putResult.getResponse().getStatus()).isEqualTo(405);

    JsonNode after = getMovie(movieId).get("data");
    assertThat(after).isEqualTo(before);
  }
}
