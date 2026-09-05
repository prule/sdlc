package com.acme.catalog.people.domain.model;

import java.util.Objects;

/**
 * A person search sort: which field, and which direction. The unique {@code id} terminal tiebreak
 * that keeps pagination deterministic is an adapter concern (see {@code
 * standards/clean-architecture.md}) — not modelled here.
 */
public record PersonSort(PersonSortField field, SortDirection direction) {

  public static final PersonSort DEFAULT = new PersonSort(PersonSortField.NAME, SortDirection.ASC);

  public PersonSort {
    Objects.requireNonNull(field, "field must not be null");
    Objects.requireNonNull(direction, "direction must not be null");
  }
}
