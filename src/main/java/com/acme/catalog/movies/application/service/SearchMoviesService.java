package com.acme.catalog.movies.application.service;

import com.acme.catalog.movies.application.port.in.SearchMoviesUseCase;
import com.acme.catalog.movies.application.port.out.LoadGenreVocabularyPort;
import com.acme.catalog.movies.application.port.out.SearchMoviesPort;
import com.acme.catalog.movies.domain.model.InvalidSearchCriterionException;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSortOrder;
import com.acme.catalog.movies.domain.model.MovieSummary;
import com.acme.catalog.movies.domain.model.SearchCriterion;
import com.acme.shared.domain.paging.Page;
import com.acme.shared.domain.paging.PageRequest;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Application service implementing {@link SearchMoviesUseCase} (UC-002, design D3). Resolves the
 * requested genres against the curated vocabulary before delegating to {@link SearchMoviesPort}, so
 * an unknown genre never reaches persistence.
 */
@Service
public class SearchMoviesService implements SearchMoviesUseCase {

  private final LoadGenreVocabularyPort loadGenreVocabularyPort;
  private final SearchMoviesPort searchMoviesPort;

  public SearchMoviesService(
      LoadGenreVocabularyPort loadGenreVocabularyPort, SearchMoviesPort searchMoviesPort) {
    this.loadGenreVocabularyPort = loadGenreVocabularyPort;
    this.searchMoviesPort = searchMoviesPort;
  }

  @Override
  public Page<MovieSummary> search(
      MovieSearchCriteria criteria, MovieSortOrder order, PageRequest pageRequest) {
    if (criteria.genres().isEmpty()) {
      return searchMoviesPort.search(criteria, order, pageRequest);
    }

    Map<String, String> vocabularyByLowerCaseName =
        loadGenreVocabularyPort.loadGenreNames().stream()
            .collect(
                Collectors.toMap(
                    name -> name.toLowerCase(Locale.ROOT),
                    name -> name,
                    // Names differing only in letter case are one genre (design, Open Questions);
                    // keep a deterministic canonical name rather than failing the whole search.
                    (first, second) -> first.compareTo(second) <= 0 ? first : second));

    Set<String> canonicalGenres =
        criteria.genres().stream()
            .map(
                requested -> {
                  String canonical =
                      vocabularyByLowerCaseName.get(requested.toLowerCase(Locale.ROOT));
                  if (canonical == null) {
                    throw new InvalidSearchCriterionException(SearchCriterion.GENRE);
                  }
                  return canonical;
                })
            .collect(Collectors.toUnmodifiableSet());

    MovieSearchCriteria resolvedCriteria =
        MovieSearchCriteria.of(
            criteria.titleTerm(),
            canonicalGenres,
            criteria.releaseYearFrom(),
            criteria.releaseYearTo(),
            criteria.minRating());

    return searchMoviesPort.search(resolvedCriteria, order, pageRequest);
  }
}
