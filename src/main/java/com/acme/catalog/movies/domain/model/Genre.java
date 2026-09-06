package com.acme.catalog.movies.domain.model;

/**
 * The catalog's controlled genre vocabulary, matching the OpenAPI {@code Genre} enum. Extending
 * this set later is additive and does not break the API contract (see {@code
 * openspec/changes/add-movie-detail/design.md} — Open Questions).
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
