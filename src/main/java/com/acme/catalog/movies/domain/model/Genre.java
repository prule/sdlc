package com.acme.catalog.movies.domain.model;

/**
 * A controlled-vocabulary category a Movie belongs to (e.g. Drama, Sci-Fi), carrying its display
 * label. Distinct from a free-form keyword.
 */
public record Genre(String label) {

  public Genre {
    if (label == null || label.isBlank()) {
      throw new IllegalArgumentException("label must not be blank");
    }
  }
}
