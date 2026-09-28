package com.acme.shared.domain.paging;

/**
 * A zero-based page request: which page, and how many items per page. Reusable by every collection
 * operation (design D3, {@code platform/collection-paging}). JDK-only: no Spring, no JPA.
 *
 * <p>The controller builds this only from values the {@code @Validated} proxy has already
 * bounds-checked, so a thrown {@link IllegalArgumentException} here is a programming error, never
 * user input reaching this constructor unvalidated (design D2).
 */
public record PageRequest(int page, int size) {

  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  public PageRequest {
    if (page < 0) {
      throw new IllegalArgumentException("page must not be negative: " + page);
    }
    if (size < 1 || size > MAX_SIZE) {
      throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE + ": " + size);
    }
  }

  /** The number of items to skip, as a {@code long} so no {@code int} overflow is possible. */
  public long offset() {
    return (long) page * (long) size;
  }
}
