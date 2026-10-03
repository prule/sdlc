package com.acme.catalog.movies.application.service;

import com.acme.catalog.movies.application.port.in.SearchMoviesQuery;
import com.acme.catalog.movies.application.port.in.SearchMoviesUseCase;
import com.acme.catalog.movies.application.port.out.LoadGenreVocabularyPort;
import com.acme.catalog.movies.application.port.out.SearchMoviesPort;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSortOrder;
import com.acme.shared.domain.InvalidRequestException;
import com.acme.shared.domain.PageSpec;
import com.acme.shared.domain.ResultPage;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Application service implementing {@link SearchMoviesUseCase} (UC-002). Every rule is checked
 * before the catalog is searched, in a fixed order — {@code sort}, {@code page}/{@code size},
 * {@code minRating}, the release-year range, then {@code genre} — so the first element at fault is
 * the one named (design D2/D4).
 */
@Service
public class SearchMoviesService implements SearchMoviesUseCase {

  private final SearchMoviesPort searchMoviesPort;
  private final LoadGenreVocabularyPort loadGenreVocabularyPort;

  public SearchMoviesService(
      SearchMoviesPort searchMoviesPort, LoadGenreVocabularyPort loadGenreVocabularyPort) {
    this.searchMoviesPort = searchMoviesPort;
    this.loadGenreVocabularyPort = loadGenreVocabularyPort;
  }

  @Override
  public ResultPage<Movie> search(SearchMoviesQuery query) {
    MovieSortOrder order = MovieSortOrder.parse(Optional.ofNullable(query.sort()));
    PageSpec page = new PageSpec(query.page(), query.size());
    MovieSearchCriteria criteria =
        MovieSearchCriteria.of(
            query.title(),
            query.genres(),
            query.releaseYearFrom(),
            query.releaseYearTo(),
            query.minRating());
    MovieSearchCriteria resolved = criteria.withGenres(canonicalGenres(criteria.genres()));
    return searchMoviesPort.search(resolved, order, page);
  }

  /** Resolves each name against the curated vocabulary, ignoring case, and de-duplicates. */
  private Set<String> canonicalGenres(Set<String> requested) {
    if (requested.isEmpty()) {
      return Set.of();
    }
    Map<String, String> vocabulary =
        loadGenreVocabularyPort.loadGenreNames().stream()
            .collect(Collectors.toMap(SearchMoviesService::key, Function.identity(), (a, b) -> a));
    Set<String> canonical = new LinkedHashSet<>();
    for (String name : requested) {
      String match = vocabulary.get(key(name));
      if (match == null) {
        throw new InvalidRequestException("genre");
      }
      canonical.add(match);
    }
    return canonical;
  }

  private static String key(String genreName) {
    return genreName.toLowerCase(Locale.ROOT);
  }
}
