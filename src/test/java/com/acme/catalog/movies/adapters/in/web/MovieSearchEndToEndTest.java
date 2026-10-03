package com.acme.catalog.movies.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.acme.testsupport.MovieCatalogFixture;
import com.acme.testsupport.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * End-to-end coverage for {@code GET /api/v1/movies} against real PostgreSQL, through every layer
 * ({@code catalog/movies} search requirements that need real data; design D5/D6).
 */
@AutoConfigureMockMvc
class MovieSearchEndToEndTest extends PostgresIntegrationTest {

  private static final String BASE = "http://localhost/api/v1/movies";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  private MovieCatalogFixture catalog;

  @BeforeEach
  void setUp() {
    catalog = new MovieCatalogFixture(jdbcTemplate);
  }

  @AfterEach
  void cleanUp() {
    catalog.clear();
  }

  private static MockHttpServletRequestBuilder search(String query) {
    return get(URI.create(query.isEmpty() ? BASE : BASE + "?" + query)).contextPath("/api/v1");
  }

  private MvcResult perform(MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc.perform(request).andReturn();
  }

  private JsonNode ok(MockHttpServletRequestBuilder request) throws Exception {
    MvcResult result = perform(request);
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(result.getResponse().getContentType()).isEqualTo("application/json");
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private static List<String> titles(JsonNode body) {
    List<String> titles = new ArrayList<>();
    body.at("/data/_embedded/movies").forEach(movie -> titles.add(movie.get("title").asText()));
    return titles;
  }

  @Test
  void browsingAnonymouslyListsEveryMovieWithItsTotals() throws Exception {
    catalog.movie("Zodiac", 2007, null);
    catalog.movie("Arrival", 2016, null);
    catalog.movie("Atonement", 2007, null);

    MvcResult result = perform(search(""));

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(titles(body)).containsExactly("Arrival", "Atonement", "Zodiac");
    assertThat(body.at("/meta/pagination").toString())
        .isEqualTo("{\"page\":0,\"size\":20,\"totalElements\":3,\"totalPages\":1}");
    assertThat(body.at("/meta/correlationId").asText())
        .isEqualTo(result.getResponse().getHeader("X-Correlation-Id"));
  }

  @Test
  void summariesCarryTheirContentAndLinkToTheDetails() throws Exception {
    UUID arrival =
        catalog.movieWithDetails(
            "Arrival", 2016, 116, "A linguist is recruited…", "4.5", "Sci-Fi", "Drama");
    catalog.movie("Untitled Reel", 1974, null);

    JsonNode movies = ok(search("")).at("/data/_embedded/movies");

    JsonNode full = movies.get(0);
    assertThat(full.get("id").asText()).isEqualTo(arrival.toString());
    assertThat(full.get("genres").toString()).isEqualTo("[\"Drama\",\"Sci-Fi\"]");
    assertThat(full.get("runtimeMinutes").asInt()).isEqualTo(116);
    assertThat(full.get("rating").toString()).isEqualTo("4.5");
    assertThat(full.has("synopsis")).isFalse();
    JsonNode minimal = movies.get(1);
    assertThat(minimal.get("genres").toString()).isEqualTo("[]");
    assertThat(minimal.has("runtimeMinutes")).isFalse();
    assertThat(minimal.has("rating")).isFalse();

    String self = full.at("/_links/self/href").asText();
    assertThat(self).isEqualTo(BASE + "/" + arrival);
    JsonNode details = ok(get(URI.create(self)).contextPath("/api/v1"));
    assertThat(details.at("/data/id").asText()).isEqualTo(arrival.toString());
  }

  @Test
  void criteriaCombineThroughEveryLayer() throws Exception {
    catalog.movie("Heist Night", 2001, "4.0", "Drama", "Thriller");
    catalog.movie("Heist Day", 2001, "3.0", "Drama", "Thriller");
    catalog.movie("Heist Morning", 2001, "4.0", "Drama");

    JsonNode body =
        ok(
            search(
                "title=heist&genre=drama&genre=THRILLER&releaseYearFrom=2000&releaseYearTo=2005"
                    + "&minRating=3.5"));

    assertThat(titles(body)).containsExactly("Heist Night");
  }

  @Test
  void aBlankTitleBrowses() throws Exception {
    catalog.movie("Arrival", 2016, null);
    catalog.movie("Brazil", 1985, null);

    assertThat(titles(ok(search("title=%20%20")))).hasSize(2);
  }

  @Test
  void aSearchMatchingNothingIsAnEmptySuccess() throws Exception {
    catalog.movie("Arrival", 2016, null);

    JsonNode body = ok(search("title=zzzz-no-such-title"));

    assertThat(body.at("/data/_embedded/movies")).isEmpty();
    assertThat(body.at("/meta/pagination/totalElements").asLong()).isZero();
    assertThat(body.at("/meta/pagination/totalPages").asInt()).isZero();
    assertThat(body.at("/data/_links/self/href").asText())
        .isEqualTo(BASE + "?title=zzzz-no-such-title");
    assertThat(body.at("/data/_links/first/href").asText()).endsWith("page=0");
    assertThat(body.at("/data/_links/last/href").asText()).endsWith("page=0");
  }

  @Test
  void anEmptyCatalogIsAnEmptySuccess() throws Exception {
    assertThat(ok(search("")).at("/data/_embedded/movies")).isEmpty();
  }

  @Test
  void pagesWalkTheResultsWithTheirLinks() throws Exception {
    for (int i = 0; i < 45; i++) {
      catalog.movie("Movie %02d".formatted(i), 2000, null, "Drama");
    }

    JsonNode first = ok(search("genre=drama"));
    assertThat(first.at("/data/_embedded/movies")).hasSize(20);
    assertThat(first.at("/meta/pagination/totalPages").asInt()).isEqualTo(3);

    List<String> walked = new ArrayList<>(titles(first));
    JsonNode page = first;
    while (page.at("/data/_links/next").isObject()) {
      page = ok(get(URI.create(page.at("/data/_links/next/href").asText())).contextPath("/api/v1"));
      walked.addAll(titles(page));
    }
    assertThat(walked).hasSize(45).doesNotHaveDuplicates();
    assertThat(page.at("/data/_links/self/href").asText()).isEqualTo(BASE + "?genre=drama&page=2");

    JsonNode afterLast = ok(search("genre=drama&page=7"));
    assertThat(afterLast.at("/data/_embedded/movies")).isEmpty();
    assertThat(afterLast.at("/meta/pagination/totalElements").asLong()).isEqualTo(45);
    assertThat(afterLast.at("/data/_links/last/href").asText())
        .isEqualTo(BASE + "?genre=drama&page=2");
  }

  @Test
  void aGenreOutsideTheVocabularyIsRefused() throws Exception {
    catalog.movie("Arrival", 2016, null, "Drama", "Sci-Fi");

    MvcResult result = perform(search("genre=drama&genre=Telenovela"));

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    String raw = result.getResponse().getContentAsString();
    JsonNode body = objectMapper.readTree(raw);
    assertThat(body.get("code").asText()).isEqualTo("BAD_REQUEST");
    assertThat(body.at("/errors/0/field").asText()).isEqualTo("genre");
    assertThat(raw).doesNotContain("Telenovela");
  }

  @Test
  void linksHonourForwardedHeaders() throws Exception {
    UUID arrival = catalog.movie("Arrival", 2016, null);

    JsonNode body =
        ok(
            search("title=arr")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "api.example.test"));

    assertThat(body.at("/data/_links/self/href").asText())
        .isEqualTo("https://api.example.test/api/v1/movies?title=arr");
    assertThat(body.at("/data/_embedded/movies/0/_links/self/href").asText())
        .isEqualTo("https://api.example.test/api/v1/movies/" + arrival);
  }
}
