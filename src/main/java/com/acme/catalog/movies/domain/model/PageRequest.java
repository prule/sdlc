package com.acme.catalog.movies.domain.model;

import com.acme.shared.domain.InvalidCriteriaException;
import com.acme.shared.domain.InvalidCriteriaException.Violation;
import java.util.ArrayList;
import java.util.List;

/**
 * Which page of an ordered list is wanted (UC-002 BR-6): pages are counted from {@code 0}, and a
 * page holds 1 to 100 entries, 20 by default. A page after the last is allowed; it is simply empty.
 */
public record PageRequest(int page, int size) {

  public static final int DEFAULT_PAGE = 0;
  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  public PageRequest {
    List<Violation> violations = new ArrayList<>();
    if (page < 0) {
      violations.add(new Violation("page", "must not be before the first page (0)"));
    }
    if (size < 1 || size > MAX_SIZE) {
      violations.add(new Violation("size", "must be between 1 and " + MAX_SIZE));
    }
    if (!violations.isEmpty()) {
      throw new InvalidCriteriaException(violations);
    }
  }

  /** Applies the defaults for whichever of {@code page} and {@code size} is {@code null}. */
  public static PageRequest of(Integer page, Integer size) {
    return new PageRequest(page == null ? DEFAULT_PAGE : page, size == null ? DEFAULT_SIZE : size);
  }

  /** The number of entries before this page; a {@code long}, so it never overflows. */
  public long offset() {
    return (long) page * size;
  }
}
