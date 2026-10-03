package com.acme.shared.domain;

/**
 * Which page of a paged list is asked for (BR-7): a zero-based {@code page} of at least {@code 0},
 * and a {@code size} from {@code 1} to {@value #MAX_SIZE}. Named {@code PageSpec}, not {@code
 * PageRequest}, so adapter code never clashes with Spring Data's type (design D2). JDK-only.
 */
public record PageSpec(int page, int size) {

  public static final int MAX_SIZE = 100;

  public PageSpec {
    if (page < 0) {
      throw new InvalidRequestException("page");
    }
    if (size < 1 || size > MAX_SIZE) {
      throw new InvalidRequestException("size");
    }
  }

  /** The number of entries before this page, as a {@code long} so a large page cannot overflow. */
  public long offset() {
    return (long) page * size;
  }
}
