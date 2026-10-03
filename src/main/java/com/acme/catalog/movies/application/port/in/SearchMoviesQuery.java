package com.acme.catalog.movies.application.port.in;

import java.math.BigDecimal;
import java.util.List;

/**
 * A movie search as asked: typed, but not yet validated against the business rules (UC-002). Any
 * value may be {@code null} when the request omitted it, except {@code genres} (empty when none),
 * {@code page} and {@code size} (already defaulted).
 */
public record SearchMoviesQuery(
    String title,
    List<String> genres,
    Integer releaseYearFrom,
    Integer releaseYearTo,
    BigDecimal minRating,
    String sort,
    int page,
    int size) {

  public SearchMoviesQuery {
    genres = genres == null ? List.of() : List.copyOf(genres);
  }
}
