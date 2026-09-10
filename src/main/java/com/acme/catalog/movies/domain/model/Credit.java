package com.acme.catalog.movies.domain.model;

import java.util.Objects;
import java.util.Optional;

/**
 * A credit linking a {@link Person} to a movie, as either a {@link Cast} (acting) or {@link Crew}
 * (non-acting) contribution (BR-4). Sealed so the two shapes stay distinct types rather than one
 * shape with a runtime discriminator flag — the two embedded relations mirror this split. No
 * Spring/JPA imports — see standards/clean-architecture.md.
 */
public sealed interface Credit permits Credit.Cast, Credit.Crew {

  Person person();

  /**
   * An acting credit: the performer, the (optional) character they played, and their billing
   * position (a positive integer where {@code 1} is top billing).
   */
  record Cast(Person person, Optional<String> character, int billingOrder) implements Credit {

    public Cast {
      Objects.requireNonNull(person, "person must not be null");
      Objects.requireNonNull(character, "character must not be null");
      if (billingOrder < 1) {
        throw new IllegalArgumentException("billingOrder must be >= 1: " + billingOrder);
      }
    }

    /**
     * Creates a {@link Cast} credit, accepting a nullable {@code character} rather than requiring
     * callers to wrap it in {@link Optional} themselves.
     */
    public static Cast of(Person person, String character, int billingOrder) {
      return new Cast(person, Optional.ofNullable(character), billingOrder);
    }
  }

  /** A non-acting credit: the contributor, their department (area of work), and their job. */
  record Crew(Person person, String department, String job) implements Credit {

    public Crew {
      Objects.requireNonNull(person, "person must not be null");
      Objects.requireNonNull(department, "department must not be null");
      Objects.requireNonNull(job, "job must not be null");
      if (department.isBlank()) {
        throw new IllegalArgumentException("department must not be blank");
      }
      if (job.isBlank()) {
        throw new IllegalArgumentException("job must not be blank");
      }
    }
  }
}
