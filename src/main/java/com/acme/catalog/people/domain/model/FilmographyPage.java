package com.acme.catalog.people.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * A page of a Person's {@link FilmographyEntry} items plus page metadata (page/size/totalElements/
 * totalPages). No link/HAL concept here — that is a web-adapter concern (see {@code
 * standards/clean-architecture.md}). Mirrors {@code
 * com.acme.catalog.movies.domain.model.MoviePage}.
 */
public record FilmographyPage(
    List<FilmographyEntry> items, int page, int size, long totalElements, int totalPages) {

  public FilmographyPage {
    Objects.requireNonNull(items, "items must not be null");
    items = List.copyOf(items);
  }
}
