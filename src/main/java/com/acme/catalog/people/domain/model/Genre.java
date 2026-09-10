package com.acme.catalog.people.domain.model;

/**
 * The catalog's controlled genre vocabulary, matching the OpenAPI {@code Genre} enum and the {@code
 * catalog.movies} slice's own {@link Genre} (see design.md decision 3: the {@code people} slice
 * defines its own value objects rather than importing {@code com.acme.catalog.movies.domain} —
 * slices stay decoupled, the same reason both slices already have their own {@code Person}).
 */
public enum Genre {
  ACTION,
  ADVENTURE,
  ANIMATION,
  COMEDY,
  CRIME,
  DOCUMENTARY,
  DRAMA,
  FAMILY,
  FANTASY,
  HISTORY,
  HORROR,
  MUSIC,
  MYSTERY,
  ROMANCE,
  SCI_FI,
  THRILLER,
  WAR,
  WESTERN
}
