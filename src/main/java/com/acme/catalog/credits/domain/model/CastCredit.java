package com.acme.catalog.credits.domain.model;

import java.util.Objects;

/**
 * An acting credit: the {@link Person} played {@code character} on the Movie, billed at {@code
 * billingOrder} (1 = top billing, ascending thereafter).
 */
public record CastCredit(Person person, String character, int billingOrder) implements Credit {

  public CastCredit {
    Objects.requireNonNull(person, "person must not be null");
    if (character == null || character.isBlank()) {
      throw new IllegalArgumentException("character must not be blank");
    }
    if (billingOrder < 1) {
      throw new IllegalArgumentException("billingOrder must be positive");
    }
  }
}
