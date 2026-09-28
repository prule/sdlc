package com.acme.shared.domain.paging;

import java.util.List;
import java.util.Objects;

/**
 * One page of a larger, ordered result: the page's items, the request that produced it, and the
 * total number of matching items across every page. Reusable by every collection operation (design
 * D3). JDK-only: no Spring, no JPA.
 */
public record Page<T>(List<T> items, PageRequest request, long totalElements) {

  public Page {
    Objects.requireNonNull(items, "items must not be null");
    Objects.requireNonNull(request, "request must not be null");
    if (totalElements < 0) {
      throw new IllegalArgumentException("totalElements must not be negative: " + totalElements);
    }
  }

  /** {@code totalElements ÷ size}, rounded up; {@code 0} when nothing matches. */
  public int totalPages() {
    return (int) Math.ceil((double) totalElements / request.size());
  }
}
