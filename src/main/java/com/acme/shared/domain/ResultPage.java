package com.acme.shared.domain;

import java.util.List;
import java.util.Objects;

/**
 * One page of a paged list (BR-7): the entries on the page plus the counts that describe the whole
 * result. A page after the last is a valid, empty page (BR-8). JDK-only.
 */
public record ResultPage<T>(List<T> items, int page, int size, long totalElements) {

  public ResultPage {
    items = List.copyOf(Objects.requireNonNull(items, "items"));
    if (page < 0) {
      throw new IllegalArgumentException("page must not be negative: " + page);
    }
    if (size < 1) {
      throw new IllegalArgumentException("size must be positive: " + size);
    }
    if (totalElements < 0) {
      throw new IllegalArgumentException("totalElements must not be negative: " + totalElements);
    }
  }

  /** An empty page that still reports the total (a page after the last, or no matches). */
  public static <T> ResultPage<T> empty(PageSpec pageSpec, long totalElements) {
    return new ResultPage<>(List.of(), pageSpec.page(), pageSpec.size(), totalElements);
  }

  /** {@code totalElements / size}, rounded up; {@code 0} when nothing matches. */
  public int totalPages() {
    return (int) ((totalElements + size - 1) / size);
  }

  /** The index of the last page; {@code 0} when nothing matches. */
  public int lastPage() {
    return Math.max(totalPages() - 1, 0);
  }

  /** Whether a previous page exists: this page is after the first and not after the last. */
  public boolean hasPrevious() {
    return page > 0 && page <= lastPage();
  }

  /** Whether a next page exists: this page is before the last. */
  public boolean hasNext() {
    return page < lastPage();
  }
}
