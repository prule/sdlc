package com.acme.catalog.movies.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.acme.catalog.movies.application.port.in.SearchMoviesQuery;
import com.acme.catalog.movies.application.port.out.LoadGenreVocabularyPort;
import com.acme.catalog.movies.application.port.out.SearchMoviesPort;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSortOrder;
import com.acme.catalog.movies.domain.model.MovieSortOrder.SortField;
import com.acme.shared.domain.InvalidRequestException;
import com.acme.shared.domain.PageSpec;
import com.acme.shared.domain.ResultPage;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchMoviesServiceTest {

  @Mock private SearchMoviesPort searchMoviesPort;
  @Mock private LoadGenreVocabularyPort loadGenreVocabularyPort;

  private SearchMoviesService service;

  @BeforeEach
  void setUp() {
    service = new SearchMoviesService(searchMoviesPort, loadGenreVocabularyPort);
    given(loadGenreVocabularyPort.loadGenreNames()).willReturn(List.of("Drama", "Sci-Fi"));
  }

  private static SearchMoviesQuery query(
      String title,
      List<String> genres,
      Integer from,
      Integer to,
      BigDecimal minRating,
      String sort,
      int page,
      int size) {
    return new SearchMoviesQuery(title, genres, from, to, minRating, sort, page, size);
  }

  private static SearchMoviesQuery defaults() {
    return query(null, List.of(), null, null, null, null, 0, 20);
  }

  @Test
  void genresResolveToTheirCanonicalNameIgnoringCaseAndAreDeduplicated() {
    given(searchMoviesPort.search(any(), any(), any()))
        .willReturn(ResultPage.empty(new PageSpec(0, 20), 0));

    service.search(query(null, List.of("drama", "DRAMA", "sci-fi"), null, null, null, null, 0, 20));

    ArgumentCaptor<MovieSearchCriteria> criteria =
        ArgumentCaptor.forClass(MovieSearchCriteria.class);
    verify(searchMoviesPort).search(criteria.capture(), any(), any());
    assertThat(criteria.getValue().genres()).containsExactly("Drama", "Sci-Fi");
  }

  @Test
  void unknownGenreIsRefusedNamingGenreAndNothingIsSearched() {
    assertThatThrownBy(
            () ->
                service.search(
                    query(null, List.of("Drama", "Spaghetti"), null, null, null, null, 0, 20)))
        .isInstanceOfSatisfying(
            InvalidRequestException.class, e -> assertThat(e.field()).isEqualTo("genre"));
    verifyNoInteractions(searchMoviesPort);
  }

  @Test
  void blankGenresAreIgnoredWithoutReadingTheVocabulary() {
    given(searchMoviesPort.search(any(), any(), any()))
        .willReturn(ResultPage.empty(new PageSpec(0, 20), 0));

    service.search(query(null, List.of("", "  "), null, null, null, null, 0, 20));

    verify(loadGenreVocabularyPort, never()).loadGenreNames();
  }

  private static Stream<Arguments> refusedQueries() {
    return Stream.of(
        Arguments.of(query(null, List.of(), 2010, 2000, null, null, 0, 20), "releaseYearFrom"),
        Arguments.of(query(null, List.of(), null, null, null, "popularity", 0, 20), "sort"),
        Arguments.of(query(null, List.of(), null, null, null, null, -1, 20), "page"),
        Arguments.of(query(null, List.of(), null, null, null, null, 0, 0), "size"),
        Arguments.of(query(null, List.of(), null, null, null, null, 0, 101), "size"),
        Arguments.of(
            query(null, List.of(), null, null, new BigDecimal("5.5"), null, 0, 20), "minRating"));
  }

  @ParameterizedTest
  @MethodSource("refusedQueries")
  void refusedQueriesNeverReachTheSearchPort(SearchMoviesQuery query, String field) {
    assertThatThrownBy(() -> service.search(query))
        .isInstanceOfSatisfying(
            InvalidRequestException.class, e -> assertThat(e.field()).isEqualTo(field));
    verifyNoInteractions(searchMoviesPort);
  }

  private static Stream<Arguments> severalInvalid() {
    BigDecimal badRating = new BigDecimal("9");
    List<String> unknownGenre = List.of("Spaghetti");
    return Stream.of(
        Arguments.of(query(null, unknownGenre, 2010, 2000, badRating, "TITLE", -1, 0), "sort"),
        Arguments.of(query(null, unknownGenre, 2010, 2000, badRating, null, -1, 0), "page"),
        Arguments.of(query(null, unknownGenre, 2010, 2000, badRating, null, 0, 0), "size"),
        Arguments.of(query(null, unknownGenre, 2010, 2000, badRating, null, 0, 20), "minRating"),
        Arguments.of(query(null, unknownGenre, 2010, 2000, null, null, 0, 20), "releaseYearFrom"),
        Arguments.of(query(null, unknownGenre, null, null, null, null, 0, 20), "genre"));
  }

  @ParameterizedTest
  @MethodSource("severalInvalid")
  void whenSeveralAreInvalidTheFirstInTheFixedOrderIsNamed(SearchMoviesQuery query, String field) {
    assertThatThrownBy(() -> service.search(query))
        .isInstanceOfSatisfying(
            InvalidRequestException.class, e -> assertThat(e.field()).isEqualTo(field));
    verifyNoInteractions(searchMoviesPort);
  }

  @Test
  void validQueryPassesCriteriaOrderAndPageThroughAndReturnsThePortsPage() {
    Movie movie =
        new Movie(
            new MovieId(UUID.randomUUID()),
            "Arrival",
            2016,
            List.of("Drama"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    ResultPage<Movie> page = new ResultPage<>(List.of(movie), 1, 10, 11);
    given(searchMoviesPort.search(any(), any(), any())).willReturn(page);

    ResultPage<Movie> result =
        service.search(
            query("arr", List.of("drama"), 2000, 2020, new BigDecimal("4"), "-rating", 1, 10));

    assertThat(result).isSameAs(page);
    verify(searchMoviesPort)
        .search(
            new MovieSearchCriteria(
                Optional.of("arr"),
                java.util.Set.of("Drama"),
                Optional.of(2000),
                Optional.of(2020),
                Optional.of(new BigDecimal("4"))),
            new MovieSortOrder(SortField.RATING, true),
            new PageSpec(1, 10));
  }

  @Test
  void defaultsSearchEverythingInTheDefaultOrder() {
    given(searchMoviesPort.search(any(), any(), any()))
        .willReturn(ResultPage.empty(new PageSpec(0, 20), 0));

    service.search(defaults());

    verify(searchMoviesPort)
        .search(
            MovieSearchCriteria.of(null, List.of(), null, null, null),
            MovieSortOrder.DEFAULT,
            new PageSpec(0, 20));
    verify(loadGenreVocabularyPort, never()).loadGenreNames();
  }

  @Test
  void portFailurePropagatesUnchanged() {
    RuntimeException failure = new RuntimeException("secret-db-host:5432 refused");
    given(searchMoviesPort.search(any(), any(), any())).willThrow(failure);

    assertThatThrownBy(() -> service.search(defaults())).isSameAs(failure);
  }
}
