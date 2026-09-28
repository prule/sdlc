package com.acme.catalog.movies.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.catalog.movies.application.port.in.GetMovieUseCase;
import com.acme.catalog.movies.application.port.in.SearchMoviesUseCase;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSortOrder;
import com.acme.catalog.movies.domain.model.MovieSummary;
import com.acme.catalog.movies.domain.model.Rating;
import com.acme.catalog.movies.domain.model.RuntimeMinutes;
import com.acme.platform.web.CollectionLinksFactory;
import com.acme.platform.web.PlatformWebTest;
import com.acme.shared.domain.paging.Page;
import com.acme.shared.domain.paging.PageRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Web-slice coverage for {@code MovieController.searchMovies}'s 200 mapping, with {@link
 * SearchMoviesUseCase} mocked (design D6, task 6.3a).
 */
@PlatformWebTest(controllers = MovieController.class)
@Import(CollectionLinksFactory.class)
class MovieSearchControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private GetMovieUseCase getMovieUseCase;
  @MockitoBean private SearchMoviesUseCase searchMoviesUseCase;

  private static final UUID MOVIE_ID = UUID.fromString("6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b");

  private MovieSummary fullyPopulatedSummary() {
    return new MovieSummary(
        new MovieId(MOVIE_ID),
        "Arrival",
        2016,
        List.of("Drama", "Sci-Fi"),
        Optional.of(new RuntimeMinutes(116)),
        Optional.of(new Rating(new BigDecimal("4.5"))));
  }

  private MovieSummary minimalSummary() {
    return new MovieSummary(
        new MovieId(MOVIE_ID),
        "Untitled Reel",
        1974,
        List.of(),
        Optional.empty(),
        Optional.empty());
  }

  @Test
  void fullyPopulatedSummaryMapsEveryMemberAndHasNoSynopsis() throws Exception {
    Page<MovieSummary> page =
        new Page<>(List.of(fullyPopulatedSummary()), new PageRequest(0, 20), 1);
    BDDMockito.given(
            searchMoviesUseCase.search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
        .willReturn(page);

    MvcResult result =
        mockMvc
            .perform(get("/api/v1/movies").contextPath("/api/v1"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();

    JsonNode movies =
        objectMapper
            .readTree(result.getResponse().getContentAsString())
            .get("data")
            .get("_embedded")
            .get("movies");
    assertThat(movies).hasSize(1);
    JsonNode summary = movies.get(0);
    assertThat(summary.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder(
            "id", "title", "releaseYear", "genres", "runtimeMinutes", "rating", "_links");
    assertThat(summary.has("synopsis")).isFalse();
    assertThat(summary.get("id").asText()).isEqualTo(MOVIE_ID.toString());
    assertThat(summary.get("rating").asText()).isEqualTo("4.5");
    assertThat(summary.get("genres")).map(JsonNode::asText).containsExactly("Drama", "Sci-Fi");
  }

  @Test
  void minimalSummaryOmitsAbsentOptionalMembersAndHasEmptyGenres() throws Exception {
    Page<MovieSummary> page = new Page<>(List.of(minimalSummary()), new PageRequest(0, 20), 1);
    BDDMockito.given(
            searchMoviesUseCase.search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
        .willReturn(page);

    MvcResult result = mockMvc.perform(get("/api/v1/movies").contextPath("/api/v1")).andReturn();

    JsonNode summary =
        objectMapper
            .readTree(result.getResponse().getContentAsString())
            .get("data")
            .get("_embedded")
            .get("movies")
            .get(0);
    assertThat(summary.get("genres").isEmpty()).isTrue();
    assertThat(summary.has("runtimeMinutes")).isFalse();
    assertThat(summary.has("rating")).isFalse();
  }

  @Test
  void eachSummarySelfLinkEqualsTheDetailsHref() throws Exception {
    Page<MovieSummary> page = new Page<>(List.of(minimalSummary()), new PageRequest(0, 20), 1);
    BDDMockito.given(
            searchMoviesUseCase.search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
        .willReturn(page);

    MvcResult result = mockMvc.perform(get("/api/v1/movies").contextPath("/api/v1")).andReturn();

    JsonNode summary =
        objectMapper
            .readTree(result.getResponse().getContentAsString())
            .get("data")
            .get("_embedded")
            .get("movies")
            .get(0);
    assertThat(summary.get("_links").get("self").get("href").asText())
        .endsWith("/api/v1/movies/" + MOVIE_ID);
  }

  @Test
  void metaCarriesPagination() throws Exception {
    Page<MovieSummary> page = new Page<>(List.of(minimalSummary()), new PageRequest(1, 7), 25);
    BDDMockito.given(
            searchMoviesUseCase.search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
        .willReturn(page);

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/movies").contextPath("/api/v1").param("page", "1").param("size", "7"))
            .andReturn();

    JsonNode meta = objectMapper.readTree(result.getResponse().getContentAsString()).get("meta");
    assertThat(meta.get("pagination").get("page").asInt()).isEqualTo(1);
    assertThat(meta.get("pagination").get("size").asInt()).isEqualTo(7);
    assertThat(meta.get("pagination").get("totalElements").asLong()).isEqualTo(25);
    assertThat(meta.get("pagination").get("totalPages").asInt()).isEqualTo(4);
  }

  @Test
  void problemJsonAcceptHeaderStillGetsA200SuccessEnvelopeAsJson() throws Exception {
    Page<MovieSummary> page = new Page<>(List.of(), new PageRequest(0, 20), 0);
    BDDMockito.given(
            searchMoviesUseCase.search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
        .willReturn(page);

    mockMvc
        .perform(
            get("/api/v1/movies").contextPath("/api/v1").accept(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON));
  }

  @Test
  void authorizationHeaderIsIgnoredAndStillGets200() throws Exception {
    Page<MovieSummary> page = new Page<>(List.of(), new PageRequest(0, 20), 0);
    BDDMockito.given(
            searchMoviesUseCase.search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
        .willReturn(page);

    mockMvc
        .perform(
            get("/api/v1/movies").contextPath("/api/v1").header("Authorization", "Bearer garbage"))
        .andExpect(status().isOk());
  }

  @Test
  void blankTitleReachesTheUseCaseAsAbsent() throws Exception {
    stubEmptySearch();

    mockMvc.perform(get("/api/v1/movies").contextPath("/api/v1").param("title", "   ")).andReturn();

    MovieSearchCriteria criteria = capturedCriteria();
    assertThat(criteria.titleTerm()).isEmpty();
  }

  @Test
  void repeatedTitleBecomesTheJoinedTerm() throws Exception {
    stubEmptySearch();

    mockMvc
        .perform(
            get("/api/v1/movies").contextPath("/api/v1").param("title", "a").param("title", "b"))
        .andReturn();

    MovieSearchCriteria criteria = capturedCriteria();
    assertThat(criteria.titleTerm()).contains("a,b");
  }

  @Test
  void rawGenreValuesAreNotCommaSplit() throws Exception {
    stubEmptySearch();

    mockMvc
        .perform(get("/api/v1/movies").contextPath("/api/v1").param("genre", "Drama,Sci-Fi"))
        .andReturn();

    MovieSearchCriteria criteria = capturedCriteria();
    assertThat(criteria.genres()).containsExactly("Drama,Sci-Fi");
  }

  @Test
  void emptyNumericParametersAndPagingAreAbsentOrDefault() throws Exception {
    stubEmptySearch();

    mockMvc
        .perform(
            get("/api/v1/movies")
                .contextPath("/api/v1")
                .param("minRating", "")
                .param("releaseYearFrom", "")
                .param("releaseYearTo", "")
                .param("page", "")
                .param("size", ""))
        .andReturn();

    MovieSearchCriteria criteria = capturedCriteria();
    assertThat(criteria.minRating()).isEmpty();
    assertThat(criteria.releaseYearFrom()).isEmpty();
    assertThat(criteria.releaseYearTo()).isEmpty();
    ArgumentCaptor<PageRequest> pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
    Mockito.verify(searchMoviesUseCase)
        .search(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            pageCaptor.capture());
    assertThat(pageCaptor.getValue()).isEqualTo(new PageRequest(0, 20));
  }

  private void stubEmptySearch() {
    Page<MovieSummary> page = new Page<>(List.of(), new PageRequest(0, 20), 0);
    BDDMockito.given(
            searchMoviesUseCase.search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
        .willReturn(page);
  }

  private MovieSearchCriteria capturedCriteria() {
    ArgumentCaptor<MovieSearchCriteria> captor = ArgumentCaptor.forClass(MovieSearchCriteria.class);
    Mockito.verify(searchMoviesUseCase)
        .search(
            captor.capture(),
            org.mockito.ArgumentMatchers.any(MovieSortOrder.class),
            org.mockito.ArgumentMatchers.any(PageRequest.class));
    return captor.getValue();
  }
}
