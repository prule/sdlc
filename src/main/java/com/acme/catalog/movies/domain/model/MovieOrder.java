package com.acme.catalog.movies.domain.model;

import com.acme.shared.domain.InvalidCriteriaException;
import java.util.Arrays;

/**
 * The orders a movie search can be listed in (UC-002 BR-5). A leading {@code -} on the parameter
 * value means descending. Every order is completed by the persistence adapter with secondary keys
 * (design D5) so it is total and stable across pages.
 */
public enum MovieOrder {
  TITLE_ASC("title"),
  TITLE_DESC("-title"),
  RELEASE_YEAR_ASC("releaseYear"),
  RELEASE_YEAR_DESC("-releaseYear"),
  RATING_ASC("rating"),
  RATING_DESC("-rating");

  /** Release year, newest first (then title A–Z). */
  public static final MovieOrder DEFAULT = RELEASE_YEAR_DESC;

  private final String parameterValue;

  MovieOrder(String parameterValue) {
    this.parameterValue = parameterValue;
  }

  public String parameterValue() {
    return parameterValue;
  }

  /**
   * @param value the requested order, or {@code null} for the default
   * @throws InvalidCriteriaException ({@code sort}) for any unsupported value
   */
  public static MovieOrder parse(String value) {
    if (value == null) {
      return DEFAULT;
    }
    return Arrays.stream(values())
        .filter(order -> order.parameterValue.equals(value))
        .findFirst()
        .orElseThrow(
            () -> new InvalidCriteriaException("sort", "must be one of the supported orders"));
  }
}
