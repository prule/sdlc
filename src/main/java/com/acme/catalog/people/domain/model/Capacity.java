package com.acme.catalog.people.domain.model;

import java.util.Objects;
import java.util.Optional;

/**
 * The single capacity in which a person contributed to a movie (BR-3, BR-4): either an {@link
 * Acting} capacity (character portrayed, optional) or a {@link NonActing} capacity (area of work
 * and specific role). Sealed so the two shapes stay distinct types rather than one shape with a
 * runtime discriminator flag, mirroring {@code catalog.movies.domain.model.Credit}. Exposes a
 * coarse {@link Type} for filtering. No Spring/JPA imports — see standards/clean-architecture.md.
 */
public sealed interface Capacity permits Capacity.Acting, Capacity.NonActing {

  /** The coarse capacity type used for filtering (BR-9). */
  enum Type {
    ACTING,
    NON_ACTING
  }

  Type type();

  /** An acting capacity: the character portrayed, when recorded. */
  record Acting(Optional<String> character) implements Capacity {

    public Acting {
      Objects.requireNonNull(character, "character must not be null");
    }

    /** Creates an {@link Acting} capacity, accepting a nullable {@code character}. */
    public static Acting of(String character) {
      return new Acting(Optional.ofNullable(character));
    }

    @Override
    public Type type() {
      return Type.ACTING;
    }
  }

  /** A non-acting capacity: the area of work (department) and the specific role (job). */
  record NonActing(String department, String job) implements Capacity {

    public NonActing {
      Objects.requireNonNull(department, "department must not be null");
      Objects.requireNonNull(job, "job must not be null");
      if (department.isBlank()) {
        throw new IllegalArgumentException("department must not be blank");
      }
      if (job.isBlank()) {
        throw new IllegalArgumentException("job must not be blank");
      }
    }

    @Override
    public Type type() {
      return Type.NON_ACTING;
    }
  }
}
