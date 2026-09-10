package com.acme.catalog.movies.adapters.out.persistence;

/** Discriminates the two credit shapes stored in the shared {@code credits} table. */
enum CreditKind {
  CAST,
  CREW
}
