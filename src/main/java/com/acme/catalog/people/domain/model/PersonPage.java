package com.acme.catalog.people.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * A page of {@link Person}s plus page metadata (page/size/totalElements/totalPages). No link/HAL
 * concept here — that is a web-adapter concern (see {@code standards/clean-architecture.md}).
 * Mirrors {@code com.acme.catalog.movies.domain.model.MoviePage}/{@code FilmographyPage}.
 */
public record PersonPage(
    List<Person> items, int page, int size, long totalElements, int totalPages) {

  public PersonPage {
    Objects.requireNonNull(items, "items must not be null");
    items = List.copyOf(items);
  }
}
