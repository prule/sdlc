package com.acme.catalog.movies.domain.model;

import java.util.Objects;

/**
 * A movie search sort: which field, and which direction. The {@code title} ascending tiebreak, and
 * the {@code NULLS LAST} handling for a {@link MovieSortField#RATING} sort in both directions, are
 * adapter concerns (see {@code standards/clean-architecture.md}) — not modelled here.
 */
public record MovieSort(MovieSortField field, SortDirection direction) {

  public static final MovieSort DEFAULT =
      new MovieSort(MovieSortField.RELEASE_YEAR, SortDirection.DESC);

  public MovieSort {
    Objects.requireNonNull(field, "field must not be null");
    Objects.requireNonNull(direction, "direction must not be null");
  }
}
