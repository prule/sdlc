package com.acme.catalog.movies.application.service;

import com.acme.catalog.movies.application.port.in.SearchMoviesQuery;
import com.acme.catalog.movies.application.port.in.SearchMoviesUseCase;
import com.acme.catalog.movies.application.port.out.GenreVocabularyPort;
import com.acme.catalog.movies.application.port.out.SearchMoviesPort;
import com.acme.catalog.movies.domain.model.MovieOrder;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSummary;
import com.acme.catalog.movies.domain.model.PageRequest;
import com.acme.catalog.movies.domain.model.ResultPage;
import com.acme.shared.domain.InvalidCriteriaException;
import com.acme.shared.domain.InvalidCriteriaException.Violation;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * Application service implementing {@link SearchMoviesUseCase} (UC-002). Decides whether the search
 * is allowed before anything is searched (design D3): the domain rules first, all their violations
 * reported together, then genre vocabulary membership, and only then the search itself.
 */
@Service
public class SearchMoviesService implements SearchMoviesUseCase {

  private final SearchMoviesPort searchMoviesPort;
  private final GenreVocabularyPort genreVocabularyPort;

  public SearchMoviesService(
      SearchMoviesPort searchMoviesPort, GenreVocabularyPort genreVocabularyPort) {
    this.searchMoviesPort = searchMoviesPort;
    this.genreVocabularyPort = genreVocabularyPort;
  }

  @Override
  public ResultPage<MovieSummary> search(SearchMoviesQuery query) {
    List<Violation> violations = new ArrayList<>();
    MovieSearchCriteria criteria =
        collecting(
            violations,
            () ->
                MovieSearchCriteria.of(
                    query.title(),
                    query.genres(),
                    query.releaseYearFrom(),
                    query.releaseYearTo(),
                    query.minRating()));
    MovieOrder order = collecting(violations, () -> MovieOrder.parse(query.sort()));
    PageRequest pageRequest =
        collecting(violations, () -> PageRequest.of(query.page(), query.size()));
    if (!violations.isEmpty()) {
      throw new InvalidCriteriaException(violations);
    }

    if (!criteria.genres().isEmpty()
        && !genreVocabularyPort.unknownGenres(criteria.genres()).isEmpty()) {
      throw new InvalidCriteriaException("genre", "must name a genre in the curated vocabulary");
    }

    return searchMoviesPort.search(criteria, order, pageRequest);
  }

  private static <T> T collecting(List<Violation> violations, Supplier<T> factory) {
    try {
      return factory.get();
    } catch (InvalidCriteriaException ex) {
      violations.addAll(ex.violations());
      return null;
    }
  }
}
