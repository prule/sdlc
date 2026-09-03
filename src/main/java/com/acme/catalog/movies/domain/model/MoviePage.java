package com.acme.catalog.movies.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * A page of {@link Movie}s plus page metadata (page/size/totalElements/totalPages). No link/HAL
 * concept here — that is a web-adapter concern (see {@code standards/clean-architecture.md}).
 * Mirrors {@code com.acme.platform.sample.domain.model.SamplePage}.
 */
public record MoviePage(List<Movie> items, int page, int size, long totalElements, int totalPages) {

  public MoviePage {
    Objects.requireNonNull(items, "items must not be null");
    items = List.copyOf(items);
  }
}
