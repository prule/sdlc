package com.acme.catalog.movies.domain.model;

import com.acme.shared.domain.DomainException;

/**
 * Raised when a movie search criterion is invalid: an unsupported {@code sort}, a reversed release-
 * year range, or an unknown genre (design D2/D3). The domain never knows HTTP parameter names; the
 * web adapter translates {@link #criterion()} to the offending query parameter.
 */
public final class InvalidSearchCriterionException extends DomainException {

  private final SearchCriterion criterion;

  public InvalidSearchCriterionException(SearchCriterion criterion) {
    super("BAD_REQUEST", "Invalid search criterion: " + criterion);
    this.criterion = criterion;
  }

  public SearchCriterion criterion() {
    return criterion;
  }
}
