package com.acme.catalog.movies.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.acme.catalog.movies.application.port.in.SearchMoviesQuery;
import com.acme.catalog.movies.application.port.out.GenreVocabularyPort;
import com.acme.catalog.movies.application.port.out.SearchMoviesPort;
import com.acme.catalog.movies.domain.model.MovieOrder;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSummary;
import com.acme.catalog.movies.domain.model.PageRequest;
import com.acme.catalog.movies.domain.model.Rating;
import com.acme.catalog.movies.domain.model.ResultPage;
import com.acme.shared.domain.InvalidCriteriaException;
import com.acme.shared.domain.InvalidCriteriaException.Violation;
import java.math.BigDecimal;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchMoviesServiceTest {

  @Mock private SearchMoviesPort searchMoviesPort;
  @Mock private GenreVocabularyPort genreVocabularyPort;
  @InjectMocks private SearchMoviesService service;

  private static SearchMoviesQuery browse() {
    return new SearchMoviesQuery(null, null, null, null, null, null, null, null);
  }

  @Test
  void browsingDelegatesWithNoCriteriaTheDefaultOrderAndTheFirstPageOfTwenty() {
    ResultPage<MovieSummary> page = new ResultPage<>(List.of(), 0, 20, 0);
    given(searchMoviesPort.search(any(), any(), any())).willReturn(page);

    assertThat(service.search(browse())).isSameAs(page);

    verify(searchMoviesPort)
        .search(
            MovieSearchCriteria.of(null, null, null, null, null),
            MovieOrder.RELEASE_YEAR_DESC,
            new PageRequest(0, 20));
    verifyNoInteractions(genreVocabularyPort);
  }

  @Test
  void passesEveryCriterionOrderAndPageThrough() {
    given(genreVocabularyPort.unknownGenres(Set.of("drama", "sci-fi"))).willReturn(Set.of());
    given(searchMoviesPort.search(any(), any(), any()))
        .willReturn(new ResultPage<>(List.of(), 2, 5, 0));

    service.search(
        new SearchMoviesQuery(
            "heist",
            List.of("Drama", "sci-fi"),
            1990,
            1999,
            new BigDecimal("3.5"),
            "-rating",
            2,
            5));

    ArgumentCaptor<MovieSearchCriteria> criteria =
        ArgumentCaptor.forClass(MovieSearchCriteria.class);
    verify(searchMoviesPort)
        .search(criteria.capture(), any(MovieOrder.class), any(PageRequest.class));
    assertThat(criteria.getValue().titleTerm()).contains("heist");
    assertThat(criteria.getValue().genres()).containsExactlyInAnyOrder("drama", "sci-fi");
    assertThat(criteria.getValue().releaseYearFrom()).isEqualTo(OptionalInt.of(1990));
    assertThat(criteria.getValue().releaseYearTo()).isEqualTo(OptionalInt.of(1999));
    assertThat(criteria.getValue().minRating()).contains(new Rating(new BigDecimal("3.5")));
    verify(searchMoviesPort)
        .search(criteria.getValue(), MovieOrder.RATING_DESC, new PageRequest(2, 5));
  }

  static List<Arguments> disallowedSearches() {
    return List.of(
        Arguments.of(
            query(null, null, null, null, null, "popularity", null, null), List.of("sort")),
        Arguments.of(query(null, null, null, null, null, null, -1, null), List.of("page")),
        Arguments.of(query(null, null, null, null, null, null, null, 0), List.of("size")),
        Arguments.of(query(null, null, null, null, null, null, null, 101), List.of("size")),
        Arguments.of(
            query(null, List.of(""), null, null, null, null, null, null), List.of("genre")),
        Arguments.of(
            query(null, null, 2000, 1990, null, null, null, null),
            List.of("releaseYearFrom", "releaseYearTo")),
        Arguments.of(
            query(null, null, null, null, new BigDecimal("5.5"), null, null, null),
            List.of("minRating")),
        Arguments.of(
            query(null, null, 2000, 1990, null, "popularity", -1, null),
            List.of("releaseYearFrom", "releaseYearTo", "sort", "page")));
  }

  private static SearchMoviesQuery query(
      String title,
      List<String> genres,
      Integer from,
      Integer to,
      BigDecimal minRating,
      String sort,
      Integer page,
      Integer size) {
    return new SearchMoviesQuery(title, genres, from, to, minRating, sort, page, size);
  }

  @ParameterizedTest
  @MethodSource("disallowedSearches")
  void refusesADisallowedSearchNamingEveryFaultWithoutSearching(
      SearchMoviesQuery query, List<String> fields) {
    assertThatThrownBy(() -> service.search(query))
        .isInstanceOfSatisfying(
            InvalidCriteriaException.class,
            ex ->
                assertThat(ex.violations())
                    .extracting(Violation::field)
                    .containsExactlyElementsOf(fields));

    verifyNoInteractions(searchMoviesPort, genreVocabularyPort);
  }

  @Test
  void refusesAGenreOutsideTheVocabularyWithoutSearching() {
    given(genreVocabularyPort.unknownGenres(Set.of("drama", "telenovela")))
        .willReturn(Set.of("telenovela"));

    assertThatThrownBy(
            () ->
                service.search(
                    query(
                        null, List.of("drama", "Telenovela"), null, null, null, null, null, null)))
        .isInstanceOfSatisfying(
            InvalidCriteriaException.class,
            ex -> {
              assertThat(ex.violations()).extracting(Violation::field).containsExactly("genre");
              assertThat(ex.violations().getFirst().message())
                  .doesNotContainIgnoringCase("telenovela");
            });

    verifyNoInteractions(searchMoviesPort);
  }
}
