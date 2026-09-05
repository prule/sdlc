package com.acme.catalog.people.domain.model;

/**
 * An acting capacity: the Person played {@code character} on the Movie, billed at {@code
 * billingOrder} (1 = top billing, ascending thereafter).
 */
public record ActingCapacity(String character, int billingOrder) implements FilmographyCapacity {

  public ActingCapacity {
    if (character == null || character.isBlank()) {
      throw new IllegalArgumentException("character must not be blank");
    }
    if (billingOrder < 1) {
      throw new IllegalArgumentException("billingOrder must be positive");
    }
  }
}
