package com.acme.catalog.credits.domain.model;

import java.util.Objects;

/**
 * A non-acting credit: the {@link Person} worked in {@code department} as {@code job} (both
 * free-text; no controlled vocabulary in this slice).
 */
public record CrewCredit(Person person, String department, String job) implements Credit {

  public CrewCredit {
    Objects.requireNonNull(person, "person must not be null");
    if (department == null || department.isBlank()) {
      throw new IllegalArgumentException("department must not be blank");
    }
    if (job == null || job.isBlank()) {
      throw new IllegalArgumentException("job must not be blank");
    }
  }
}
