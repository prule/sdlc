package com.acme.catalog.movies.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.catalog.movies.application.port.in.GetMovieUseCase;
import com.acme.catalog.movies.application.port.in.SearchMoviesUseCase;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.catalog.movies.domain.model.Rating;
import com.acme.catalog.movies.domain.model.RuntimeMinutes;
import com.acme.platform.web.CollectionLinksFactory;
import com.acme.platform.web.PlatformWebTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Web-slice coverage for the {@code getMovie} 200 mapping (design D2, {@code catalog/movies}
 * "Retrieve a movie's details" and "Movie details contain exactly the curated movie information").
 * {@link GetMovieUseCase} is mocked; persistence and runtime-mode behaviour are covered elsewhere.
 */
@PlatformWebTest(controllers = MovieController.class)
@org.springframework.context.annotation.Import(CollectionLinksFactory.class)
class MovieControllerTest {

  private static final UUID MOVIE_ID = UUID.fromString("6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private GetMovieUseCase getMovieUseCase;
  @MockitoBean private SearchMoviesUseCase searchMoviesUseCase;

  private static Movie fullyPopulatedMovie() {
    return new Movie(
        new MovieId(MOVIE_ID),
        "Arrival",
        2016,
        List.of("Thriller", "Drama", "Sci-Fi"),
        Optional.of(new RuntimeMinutes(116)),
        Optional.of("A linguist is recruited to communicate with alien visitors."),
        Optional.of(new Rating(new BigDecimal("4.5"))));
  }

  private static Movie minimalMovie() {
    return new Movie(
        new MovieId(MOVIE_ID),
        "Untitled Reel",
        1974,
        List.of(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  @Test
  void fullyPopulatedMovieMapsEveryMember() throws Exception {
    BDDMockito.given(getMovieUseCase.getMovie(new MovieId(MOVIE_ID)))
        .willReturn(fullyPopulatedMovie());

    MvcResult result =
        mockMvc
            .perform(get("/api/v1/movies/{id}", MOVIE_ID).contextPath("/api/v1"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();

    JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
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
    assertThat(data.get("id").asText()).isEqualTo(MOVIE_ID.toString());
    assertThat(data.get("title").asText()).isEqualTo("Arrival");
    assertThat(data.get("releaseYear").asInt()).isEqualTo(2016);
    assertThat(data.get("genres"))
        .map(JsonNode::asText)
        .containsExactly("Drama", "Sci-Fi", "Thriller");
    assertThat(data.get("runtimeMinutes").asInt()).isEqualTo(116);
    assertThat(data.get("synopsis").asText())
        .isEqualTo("A linguist is recruited to communicate with alien visitors.");
    assertThat(data.get("_links").get("self").get("href").asText())
        .endsWith("/api/v1/movies/" + MOVIE_ID);
  }

  @Test
  void minimalMovieOmitsAbsentOptionalMembersAndHasEmptyGenres() throws Exception {
    BDDMockito.given(getMovieUseCase.getMovie(new MovieId(MOVIE_ID))).willReturn(minimalMovie());

    MvcResult result =
        mockMvc
            .perform(get("/api/v1/movies/{id}", MOVIE_ID).contextPath("/api/v1"))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    assertThat(data.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder("id", "title", "releaseYear", "genres", "_links");
    assertThat(data.get("genres").isEmpty()).isTrue();
    assertThat(data.has("runtimeMinutes")).isFalse();
    assertThat(data.has("synopsis")).isFalse();
    assertThat(data.has("rating")).isFalse();
  }

  private static Stream<Arguments> ratingLiterals() {
    return Stream.of(
        Arguments.of(new BigDecimal("5.0"), "5"),
        Arguments.of(new BigDecimal("4.5"), "4.5"),
        Arguments.of(new BigDecimal("0.0"), "0"));
  }

  @ParameterizedTest
  @MethodSource("ratingLiterals")
  void ratingIsWrittenInItsShortestExactDecimalForm(BigDecimal stored, String expectedLiteral)
      throws Exception {
    Movie movie =
        new Movie(
            new MovieId(MOVIE_ID),
            "Some Movie",
            2000,
            List.of(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(new Rating(stored)));
    BDDMockito.given(getMovieUseCase.getMovie(new MovieId(MOVIE_ID))).willReturn(movie);

    MvcResult result =
        mockMvc.perform(get("/api/v1/movies/{id}", MOVIE_ID).contextPath("/api/v1")).andReturn();

    String raw = result.getResponse().getContentAsString();
    JsonNode ratingNode = objectMapper.readTree(raw).get("data").get("rating");
    assertThat(ratingNode.asText()).isEqualTo(expectedLiteral);
  }

  @Test
  void selfLinkForAPlainRequest() throws Exception {
    BDDMockito.given(getMovieUseCase.getMovie(new MovieId(MOVIE_ID))).willReturn(minimalMovie());

    MvcResult result =
        mockMvc.perform(get("/api/v1/movies/{id}", MOVIE_ID).contextPath("/api/v1")).andReturn();

    JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    assertThat(data.get("_links").fieldNames()).toIterable().containsExactly("self");
    assertThat(data.get("_links").get("self").get("href").asText())
        .endsWith("/api/v1/movies/" + MOVIE_ID);
  }

  @Test
  void selfLinkHonoursForwardedHeaders() throws Exception {
    BDDMockito.given(getMovieUseCase.getMovie(new MovieId(MOVIE_ID))).willReturn(minimalMovie());

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/movies/{id}", MOVIE_ID)
                    .contextPath("/api/v1")
                    .header("X-Forwarded-Proto", "https")
                    .header("X-Forwarded-Host", "api.example.test"))
            .andReturn();

    JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    assertThat(data.get("_links").get("self").get("href").asText())
        .isEqualTo("https://api.example.test/api/v1/movies/" + MOVIE_ID);
  }

  @Test
  void problemJsonAcceptHeaderStillGetsA200SuccessEnvelopeAsJson() throws Exception {
    BDDMockito.given(getMovieUseCase.getMovie(new MovieId(MOVIE_ID))).willReturn(minimalMovie());

    mockMvc
        .perform(
            get("/api/v1/movies/{id}", MOVIE_ID)
                .contextPath("/api/v1")
                .accept(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON));
  }

  @Test
  void upperCaseIdentifierIsCanonicalised() throws Exception {
    BDDMockito.given(getMovieUseCase.getMovie(new MovieId(MOVIE_ID))).willReturn(minimalMovie());

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/movies/{id}", MOVIE_ID.toString().toUpperCase())
                    .contextPath("/api/v1"))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    assertThat(data.get("id").asText()).isEqualTo(MOVIE_ID.toString());
    assertThat(data.get("_links").get("self").get("href").asText())
        .endsWith("/api/v1/movies/" + MOVIE_ID);
  }
}
