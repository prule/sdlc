package com.acme.catalog.movies.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.acme.testsupport.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * End-to-end coverage for {@code GET /api/v1/movies} against real PostgreSQL, exercising every
 * {@code catalog/movies} search scenario that needs real data.
 */
@AutoConfigureMockMvc
class MovieSearchEndToEndTest extends PostgresIntegrationTest {

  private static final String CONTEXT_PATH = "/api/v1";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update("DELETE FROM movie_genre");
    jdbcTemplate.update("DELETE FROM movie");
    jdbcTemplate.update("DELETE FROM genre");
  }

  private UUID genre(String name) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update("INSERT INTO genre (id, name) VALUES (?, ?)", id, name);
    return id;
  }

  private UUID movie(String title, int releaseYear, String rating, UUID... genres) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO movie (id, title, release_year, runtime_minutes, synopsis, rating) "
            + "VALUES (?, ?, ?, ?, ?, ?)",
        id,
        title,
        releaseYear,
        null,
        null,
        rating == null ? null : new BigDecimal(rating));
    for (UUID genre : genres) {
      jdbcTemplate.update("INSERT INTO movie_genre (movie_id, genre_id) VALUES (?, ?)", id, genre);
    }
    return id;
  }

  private void movies(int count) {
    for (int i = 0; i < count; i++) {
      movie("Movie" + (char) ('A' + i), 1990 + i, null);
    }
  }

  private static MockHttpServletRequestBuilder search() {
    return get(CONTEXT_PATH + "/movies").contextPath(CONTEXT_PATH);
  }

  private MvcResult perform(MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc.perform(request).andReturn();
  }

  private JsonNode ok(MockHttpServletRequestBuilder request) throws Exception {
    MvcResult result = perform(request);
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  /** Follows an absolute href through MockMvc (its path already carries the context path). */
  private JsonNode follow(String href) throws Exception {
    URI uri = URI.create(href);
    String pathAndQuery =
        uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
    return ok(get(URI.create(pathAndQuery)).contextPath(CONTEXT_PATH));
  }

  private static List<String> titles(JsonNode body) {
    List<String> titles = new ArrayList<>();
    body.at("/data/_embedded/movies").forEach(m -> titles.add(m.get("title").asText()));
    return titles;
  }

  @Test
  void browsingTwentyFiveMoviesReturnsTheFirstPageOfTwentyWithTotals() throws Exception {
    movies(25);

    MvcResult result = perform(search());

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(result.getResponse().getContentType()).isEqualTo("application/json");
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.at("/data/_embedded/movies")).hasSize(20);
    JsonNode pagination = body.at("/meta/pagination");
    assertThat(pagination.get("page").asInt()).isZero();
    assertThat(pagination.get("size").asInt()).isEqualTo(20);
    assertThat(pagination.get("totalElements").asLong()).isEqualTo(25);
    assertThat(pagination.get("totalPages").asInt()).isEqualTo(2);
    assertThat(body.at("/meta/correlationId").asText())
        .isEqualTo(result.getResponse().getHeader("X-Correlation-Id"));
  }

  @Test
  void criteriaCombineToNarrow() throws Exception {
    UUID drama = genre("Drama");
    UUID sciFi = genre("Sci-Fi");
    movie("Arrival", 2016, "4.5", drama, sciFi);
    movie("Arrival Point", 1999, "4.5", drama);
    movie("Contact", 1997, "4", drama, sciFi);

    JsonNode body =
        ok(
            search()
                .param("title", "arrival")
                .param("genre", "Sci-Fi")
                .param("releaseYearFrom", "2000")
                .param("minRating", "4"));

    assertThat(titles(body)).containsExactly("Arrival");
    assertThat(body.at("/meta/pagination/totalElements").asLong()).isEqualTo(1);
  }

  @Test
  void genreIsRecognisedIgnoringCase() throws Exception {
    UUID drama = genre("Drama");
    movie("Atonement", 2007, null, drama);
    movie("Contact", 1997, null);

    assertThat(titles(ok(search().param("genre", "drama"))))
        .isEqualTo(titles(ok(search().param("genre", "Drama"))))
        .containsExactly("Atonement");
  }

  @Test
  void severalGenresMeanAllOfThemAndAMovieWithoutGenresNeverMatches() throws Exception {
    UUID drama = genre("Drama");
    UUID sciFi = genre("Sci-Fi");
    movie("Arrival", 2016, null, drama, sciFi);
    movie("Contact", 1997, null, sciFi);
    movie("Untitled Reel", 1974, null);

    assertThat(titles(ok(search().param("genre", "Drama", "Sci-Fi")))).containsExactly("Arrival");
    assertThat(titles(ok(search().param("genre", "Drama,Sci-Fi")))).containsExactly("Arrival");
    assertThat(titles(ok(search().param("genre", "Drama")))).containsExactly("Arrival");
  }

  @Test
  void unknownGenreIsRefusedNamingGenre() throws Exception {
    genre("Drama");

    MvcResult result = perform(search().param("genre", "Spaghetti"));

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("code").asText()).isEqualTo("BAD_REQUEST");
    assertThat(body.get("detail").asText())
        .isEqualTo("The request parameter 'genre' is not valid.")
        .doesNotContain("Spaghetti");
  }

  @Test
  void titleTermScenarios() throws Exception {
    movie("Arrival", 2016, null);
    movie("The Arrival of a Train", 1896, null);
    movie("Contact", 1997, null);
    movie("100% Wolf", 2020, null);
    movie("1000 Wolves", 2010, null);

    assertThat(titles(ok(search().param("title", "ARRIV"))))
        .containsExactlyInAnyOrder("Arrival", "The Arrival of a Train");
    assertThat(titles(ok(search().param("title", "0% W")))).containsExactly("100% Wolf");
    assertThat(ok(search().param("title", "  ")).at("/meta/pagination/totalElements").asLong())
        .isEqualTo(5);
  }

  @Test
  void emptyCatalogIsAnEmptySuccess() throws Exception {
    JsonNode body = ok(search());

    assertThat(body.at("/data/_embedded/movies").isArray()).isTrue();
    assertThat(body.at("/data/_embedded/movies").isEmpty()).isTrue();
    assertThat(body.at("/meta/pagination/totalElements").asLong()).isZero();
    assertThat(body.at("/meta/pagination/totalPages").asInt()).isZero();
  }

  @Test
  void noMatchIsAnEmptySuccess() throws Exception {
    movies(3);

    JsonNode body = ok(search().param("title", "zzzz-no-such-title"));

    assertThat(body.at("/data/_embedded/movies").isEmpty()).isTrue();
    assertThat(body.at("/meta/pagination/totalElements").asLong()).isZero();
    assertThat(body.at("/meta/pagination/totalPages").asInt()).isZero();
    assertThat(body.at("/data/_links/first").isMissingNode()).isFalse();
    assertThat(body.at("/data/_links/last").isMissingNode()).isFalse();
  }

  @Test
  void pagingTotalsAndThePageAfterTheLast() throws Exception {
    movies(5);

    JsonNode lastPartial = ok(search().param("size", "2").param("page", "2"));
    assertThat(lastPartial.at("/data/_embedded/movies")).hasSize(1);
    assertThat(lastPartial.at("/meta/pagination/totalPages").asInt()).isEqualTo(3);

    JsonNode afterLast = ok(search().param("size", "2").param("page", "9"));
    assertThat(afterLast.at("/data/_embedded/movies")).isEmpty();
    assertThat(afterLast.at("/meta/pagination/totalElements").asLong()).isEqualTo(5);
    assertThat(afterLast.at("/meta/pagination/totalPages").asInt()).isEqualTo(3);
    assertThat(afterLast.at("/data/_links").fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder("self", "first", "last");

    JsonNode single = ok(search().param("size", "100"));
    assertThat(single.at("/data/_embedded/movies")).hasSize(5);
    assertThat(single.at("/meta/pagination/totalPages").asInt()).isEqualTo(1);
  }

  @Test
  void linksPreserveCriteriaAndOrder() throws Exception {
    UUID drama = genre("Drama");
    UUID sciFi = genre("Sci-Fi");
    for (int i = 0; i < 45; i++) {
      movie("The Movie" + i, 1950 + i, i % 2 == 0 ? "4.0" : null, drama, sciFi);
    }

    JsonNode links =
        ok(search()
                .param("title", "the")
                .param("genre", "Drama", "Sci-Fi")
                .param("sort", "-rating")
                .param("size", "10")
                .param("page", "1"))
            .at("/data/_links");

    String query = "title=the&genre=Drama&genre=Sci-Fi&sort=-rating&size=10&page=";
    assertThat(links.at("/self/href").asText()).endsWith("/api/v1/movies?" + query + "1");
    assertThat(links.at("/first/href").asText()).endsWith("?" + query + "0");
    assertThat(links.at("/prev/href").asText()).endsWith("?" + query + "0");
    assertThat(links.at("/next/href").asText()).endsWith("?" + query + "2");
    assertThat(links.at("/last/href").asText()).endsWith("?" + query + "4");
  }

  @Test
  void followingNextToTheEndVisitsEveryMovieOnceWithStableTotals() throws Exception {
    movies(7);

    JsonNode page = ok(search().param("size", "3"));
    List<String> seen = new ArrayList<>(titles(page));
    int pages = 1;
    while (!page.at("/data/_links/next").isMissingNode()) {
      page = follow(page.at("/data/_links/next/href").asText());
      assertThat(page.at("/meta/pagination/totalElements").asLong()).isEqualTo(7);
      seen.addAll(titles(page));
      pages++;
    }

    assertThat(pages).isEqualTo(3);
    assertThat(seen).hasSize(7).doesNotHaveDuplicates();
    assertThat(seen).isEqualTo(titles(ok(search().param("size", "100"))));
  }

  @Test
  void followingAnEntrysSelfLinkRetrievesThatMovie() throws Exception {
    UUID id = movie("Arrival", 2016, "4.5");

    JsonNode entry = ok(search()).at("/data/_embedded/movies/0");
    JsonNode details = follow(entry.at("/_links/self/href").asText());

    assertThat(entry.get("id").asText()).isEqualTo(id.toString());
    assertThat(details.at("/data/id").asText()).isEqualTo(id.toString());
  }

  @Test
  void summaryMembersForFullAndMinimalMovies() throws Exception {
    UUID drama = genre("Drama");
    UUID sciFi = genre("Sci-Fi");
    UUID full = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO movie (id, title, release_year, runtime_minutes, synopsis, rating) "
            + "VALUES (?, ?, ?, ?, ?, ?)",
        full,
        "Arrival",
        2016,
        116,
        "A linguist is recruited...",
        new BigDecimal("4.5"));
    jdbcTemplate.update("INSERT INTO movie_genre (movie_id, genre_id) VALUES (?, ?)", full, sciFi);
    jdbcTemplate.update("INSERT INTO movie_genre (movie_id, genre_id) VALUES (?, ?)", full, drama);
    movie("Untitled Reel", 1974, null);

    JsonNode movies = ok(search()).at("/data/_embedded/movies");

    JsonNode arrival = movies.get(0);
    assertThat(arrival.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder(
            "id", "title", "releaseYear", "genres", "runtimeMinutes", "rating", "_links");
    assertThat(arrival.get("runtimeMinutes").asInt()).isEqualTo(116);
    assertThat(arrival.get("rating").asText()).isEqualTo("4.5");
    assertThat(arrival.get("genres")).map(JsonNode::asText).containsExactly("Drama", "Sci-Fi");
    assertThat(arrival.at("/_links/self/href").asText()).endsWith("/api/v1/movies/" + full);

    JsonNode reel = movies.get(1);
    assertThat(reel.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder("id", "title", "releaseYear", "genres", "_links");
    assertThat(reel.get("genres").isEmpty()).isTrue();
  }

  @Test
  void searchIsPublicAndAPresentedCredentialIsNeverValidated() throws Exception {
    movies(2);

    // No Authorization header at all ("Anonymous search").
    assertThat(ok(search()).at("/meta/pagination/totalElements").asLong()).isEqualTo(2);
    // A garbage credential is neither validated nor refused ("Search is public and read-only").
    assertThat(
            ok(search().header("Authorization", "Bearer not-a-real-token"))
                .at("/meta/pagination/totalElements")
                .asLong())
        .isEqualTo(2);
    assertThat(
            ok(search().header("Authorization", "Basic Zm9vOmJhcg=="))
                .at("/meta/pagination/totalElements")
                .asLong())
        .isEqualTo(2);
  }

  @Test
  void commaSeparatedAndBlankGenreValuesBehaveLikeRepeatedOnes() throws Exception {
    UUID drama = genre("Drama");
    UUID sciFi = genre("Sci-Fi");
    movie("Arrival", 2016, "4.5", drama, sciFi);
    movie("Contact", 1997, "4.0", sciFi);
    movie("Atonement", 2007, "4.0", drama);

    List<String> repeated = titles(ok(search().param("genre", "Drama").param("genre", "Sci-Fi")));
    assertThat(repeated).containsExactly("Arrival");
    // Comma-separated is the same as repeating the parameter.
    assertThat(titles(ok(search().param("genre", "drama,SCI-FI")))).isEqualTo(repeated);
    // The same genre repeated in any letter case is the same as giving it once.
    assertThat(titles(ok(search().param("genre", "Drama").param("genre", "DRAMA"))))
        .containsExactly("Arrival", "Atonement");
    // Blank genre values are ignored, not refused.
    assertThat(titles(ok(search().param("genre", "  ").param("genre", "Sci-Fi"))))
        .containsExactly("Arrival", "Contact");
    assertThat(ok(search().param("genre", "")).at("/meta/pagination/totalElements").asLong())
        .isEqualTo(3);
  }

  @Test
  void anonymousPostIsRefusedAndTheCatalogIsUnchanged() throws Exception {
    movies(3);
    long before = ok(search()).at("/meta/pagination/totalElements").asLong();

    MvcResult refused =
        perform(
            post(CONTEXT_PATH + "/movies")
                .contextPath(CONTEXT_PATH)
                .contentType("application/json")
                .content("{\"title\":\"Injected\",\"releaseYear\":2020}"));

    assertThat(refused.getResponse().getStatus()).isEqualTo(405);
    assertThat(refused.getResponse().getHeader("Allow")).contains("GET");
    assertThat(
            objectMapper.readTree(refused.getResponse().getContentAsString()).get("code").asText())
        .isEqualTo("METHOD_NOT_ALLOWED");
    assertThat(ok(search()).at("/meta/pagination/totalElements").asLong()).isEqualTo(before);
  }
}
