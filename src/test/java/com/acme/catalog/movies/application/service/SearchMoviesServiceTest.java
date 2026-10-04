package com.acme.catalog.movies.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.acme.catalog.movies.application.port.out.LoadGenreVocabularyPort;
import com.acme.catalog.movies.application.port.out.SearchMoviesPort;
import com.acme.catalog.movies.domain.model.InvalidSearchCriterionException;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSortOrder;
import com.acme.catalog.movies.domain.model.SearchCriterion;
import com.acme.shared.domain.paging.Page;
import com.acme.shared.domain.paging.PageRequest;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link SearchMoviesService}, with mocked ports (design D3, task 4.1). */
@ExtendWith(MockitoExtension.class)
class SearchMoviesServiceTest {

  @Mock private LoadGenreVocabularyPort loadGenreVocabularyPort;
  @Mock private SearchMoviesPort searchMoviesPort;

  private final PageRequest pageRequest = new PageRequest(0, 20);

  @Test
  void knownGenresInAnyCaseAreResolvedToCanonicalNamesAndPassedToTheSearchPort() {
    given(loadGenreVocabularyPort.loadGenreNames()).willReturn(Set.of("Drama", "Sci-Fi"));
    given(searchMoviesPort.search(any(), any(), any()))
        .willReturn(new Page<>(List.of(), pageRequest, 0));
    SearchMoviesService service =
        new SearchMoviesService(loadGenreVocabularyPort, searchMoviesPort);
    MovieSearchCriteria criteria =
        MovieSearchCriteria.of(
            Optional.empty(),
            Set.of("drama", "SCI-FI"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    service.search(criteria, MovieSortOrder.DEFAULT, pageRequest);

    ArgumentCaptor<MovieSearchCriteria> captor = ArgumentCaptor.forClass(MovieSearchCriteria.class);
    org.mockito.Mockito.verify(searchMoviesPort)
        .search(
            captor.capture(),
            org.mockito.ArgumentMatchers.eq(MovieSortOrder.DEFAULT),
            org.mockito.ArgumentMatchers.eq(pageRequest));
    assertThat(captor.getValue().genres()).containsExactlyInAnyOrder("Drama", "Sci-Fi");
  }

  @Test
  void vocabularyNamesDifferingOnlyInCaseAreOneGenreNotAFailure() {
    given(loadGenreVocabularyPort.loadGenreNames()).willReturn(Set.of("Drama", "drama"));
    given(searchMoviesPort.search(any(), any(), any()))
        .willReturn(new Page<>(List.of(), pageRequest, 0));
    SearchMoviesService service =
        new SearchMoviesService(loadGenreVocabularyPort, searchMoviesPort);
    MovieSearchCriteria criteria =
        MovieSearchCriteria.of(
            Optional.empty(),
            Set.of("DRAMA"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    service.search(criteria, MovieSortOrder.DEFAULT, pageRequest);

    ArgumentCaptor<MovieSearchCriteria> captor = ArgumentCaptor.forClass(MovieSearchCriteria.class);
    org.mockito.Mockito.verify(searchMoviesPort).search(captor.capture(), any(), any());
    assertThat(captor.getValue().genres()).containsExactly("Drama");
  }

  @Test
  void anUnknownGenreThrowsAndTheSearchPortIsNeverInvoked() {
    given(loadGenreVocabularyPort.loadGenreNames()).willReturn(Set.of("Drama"));
    SearchMoviesService service =
        new SearchMoviesService(loadGenreVocabularyPort, searchMoviesPort);
    MovieSearchCriteria criteria =
        MovieSearchCriteria.of(
            Optional.empty(),
            Set.of("Western"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    assertThatThrownBy(() -> service.search(criteria, MovieSortOrder.DEFAULT, pageRequest))
        .isInstanceOf(InvalidSearchCriterionException.class)
        .extracting(ex -> ((InvalidSearchCriterionException) ex).criterion())
        .isEqualTo(SearchCriterion.GENRE);
    verifyNoInteractions(searchMoviesPort);
  }

  @Test
  void anEmptyGenreThrowsAndTheSearchPortIsNeverInvoked() {
    given(loadGenreVocabularyPort.loadGenreNames()).willReturn(Set.of("Drama"));
    SearchMoviesService service =
        new SearchMoviesService(loadGenreVocabularyPort, searchMoviesPort);
    MovieSearchCriteria criteria =
        MovieSearchCriteria.of(
            Optional.empty(), Set.of(""), Optional.empty(), Optional.empty(), Optional.empty());

    assertThatThrownBy(() -> service.search(criteria, MovieSortOrder.DEFAULT, pageRequest))
        .isInstanceOf(InvalidSearchCriterionException.class)
        .extracting(ex -> ((InvalidSearchCriterionException) ex).criterion())
        .isEqualTo(SearchCriterion.GENRE);
    verifyNoInteractions(searchMoviesPort);
  }

  @Test
  void noGenresSkipsTheVocabularyLookup() {
    given(searchMoviesPort.search(any(), any(), any()))
        .willReturn(new Page<>(List.of(), pageRequest, 0));
    SearchMoviesService service =
        new SearchMoviesService(loadGenreVocabularyPort, searchMoviesPort);
    MovieSearchCriteria criteria =
        MovieSearchCriteria.of(
            Optional.empty(), Set.of(), Optional.empty(), Optional.empty(), Optional.empty());

    service.search(criteria, MovieSortOrder.DEFAULT, pageRequest);

    verifyNoInteractions(loadGenreVocabularyPort);
  }

  @Test
  void aPortExceptionPropagates() {
    RuntimeException portFailure = new RuntimeException("secret-db-host:5432 refused");
    given(searchMoviesPort.search(any(), any(), any())).willThrow(portFailure);
    SearchMoviesService service =
        new SearchMoviesService(loadGenreVocabularyPort, searchMoviesPort);
    MovieSearchCriteria criteria =
        MovieSearchCriteria.of(
            Optional.empty(), Set.of(), Optional.empty(), Optional.empty(), Optional.empty());

    assertThatThrownBy(() -> service.search(criteria, MovieSortOrder.DEFAULT, pageRequest))
        .isSameAs(portFailure);
  }
}
