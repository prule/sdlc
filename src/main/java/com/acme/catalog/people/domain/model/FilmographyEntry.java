package com.acme.catalog.people.domain.model;

import java.util.Objects;

/**
 * One filmography entry: a movie summary annotated with exactly one {@link Capacity} (BR-3, BR-4).
 * A person credited in several capacities on the same movie yields several entries, never one entry
 * listing several capacities.
 */
public record FilmographyEntry(FilmographyMovieSummary movie, Capacity capacity) {

  public FilmographyEntry {
    Objects.requireNonNull(movie, "movie must not be null");
    Objects.requireNonNull(capacity, "capacity must not be null");
  }
}
