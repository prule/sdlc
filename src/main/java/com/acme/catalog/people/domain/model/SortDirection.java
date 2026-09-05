package com.acme.catalog.people.domain.model;

/**
 * Ascending or descending sort direction, scoped to {@code catalog.people}. Deliberately not the
 * {@code com.acme.catalog.movies.domain.model.SortDirection} enum — that type is movies-domain
 * scoped and importing it here would be a cross-feature domain coupling (see {@code
 * standards/clean-architecture.md}). No Spring/JPA imports — pure domain.
 */
public enum SortDirection {
  ASC,
  DESC
}
