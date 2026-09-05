package com.acme.catalog.people.domain.model;

/**
 * A non-acting capacity: the Person worked in {@code department} as {@code job} (both free-text; no
 * controlled vocabulary in this slice).
 */
public record NonActingCapacity(String department, String job) implements FilmographyCapacity {

  public NonActingCapacity {
    if (department == null || department.isBlank()) {
      throw new IllegalArgumentException("department must not be blank");
    }
    if (job == null || job.isBlank()) {
      throw new IllegalArgumentException("job must not be blank");
    }
  }
}
