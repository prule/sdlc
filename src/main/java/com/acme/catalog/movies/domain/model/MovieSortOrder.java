package com.acme.catalog.movies.domain.model;

import java.util.Optional;

/**
 * The order search results are returned in (design D3/D4). Absent means {@link #DEFAULT}; any other
 * value throws {@link InvalidSearchCriterionException} with {@link SearchCriterion#SORT}.
 */
public enum MovieSortOrder {
  DEFAULT,
  TITLE_ASC,
  TITLE_DESC,
  RELEASE_YEAR_ASC,
  RELEASE_YEAR_DESC,
  RATING_ASC,
  RATING_DESC;

  public static MovieSortOrder parse(Optional<String> raw) {
    if (raw.isEmpty()) {
      return DEFAULT;
    }
    return switch (raw.get()) {
      case "title" -> TITLE_ASC;
      case "-title" -> TITLE_DESC;
      case "releaseYear" -> RELEASE_YEAR_ASC;
      case "-releaseYear" -> RELEASE_YEAR_DESC;
      case "rating" -> RATING_ASC;
      case "-rating" -> RATING_DESC;
      default -> throw new InvalidSearchCriterionException(SearchCriterion.SORT);
    };
  }
}
