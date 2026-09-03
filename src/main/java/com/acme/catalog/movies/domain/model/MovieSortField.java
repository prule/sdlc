package com.acme.catalog.movies.domain.model;

/** The field a movie search result can be sorted by. No Spring/JPA imports — pure domain. */
public enum MovieSortField {
  TITLE,
  RELEASE_YEAR,
  RATING
}
