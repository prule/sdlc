package com.acme.catalog.people.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * A page of a person's {@link FilmographyEntry} items plus page metadata (page/size/totalElements),
 * with {@code totalPages} derived. No link/HAL concept here — that is a web-adapter concern (see
 * standards/clean-architecture.md).
 */
public record Filmography(List<FilmographyEntry> content, int page, int size, long totalElements) {

  public Filmography {
    Objects.requireNonNull(content, "content must not be null");
    content = List.copyOf(content);
  }

  /** The total number of pages, derived from {@code totalElements} and {@code size}. */
  public int totalPages() {
    if (size <= 0) {
      return 0;
    }
    return (int) Math.ceil((double) totalElements / size);
  }
}
