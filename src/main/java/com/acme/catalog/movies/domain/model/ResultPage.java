package com.acme.catalog.movies.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * One page of an ordered list plus the counts a consumer needs to navigate it (UC-002 BR-6, BR-7).
 * {@code page} and {@code size} are as requested; {@code totalElements} counts matches across all
 * pages.
 */
public record ResultPage<T>(List<T> items, int page, int size, long totalElements) {

  public ResultPage {
    Objects.requireNonNull(items, "items must not be null");
    if (page < 0 || size < 1 || totalElements < 0) {
      throw new IllegalArgumentException("page, size and totalElements must be in range");
    }
    items = List.copyOf(items);
  }

  public static <T> ResultPage<T> of(List<T> items, PageRequest request, long totalElements) {
    return new ResultPage<>(items, request.page(), request.size(), totalElements);
  }

  /** {@code totalElements / size}, rounded up; {@code 0} when nothing matches. */
  public int totalPages() {
    return Math.toIntExact((totalElements + size - 1) / size);
  }

  /** The index of the last page; {@code 0} when nothing matches. */
  public int lastPage() {
    return Math.max(totalPages() - 1, 0);
  }

  /** A previous page exists from page 1 up to the page just after the last. */
  public boolean hasPrev() {
    return page >= 1 && page <= lastPage() + 1;
  }

  public boolean hasNext() {
    return page < lastPage();
  }
}
