package com.acme.catalog.movies.domain.model;

import com.acme.shared.domain.InvalidRequestException;
import java.util.Objects;
import java.util.Optional;

/**
 * The order a movie search lists its results in (UC-002, BR-5). Parsed from exactly six
 * case-sensitive values: {@code title}, {@code releaseYear} or {@code rating}, each optionally
 * prefixed with {@code -} for descending. Absent means {@code -releaseYear} (design D4).
 */
public record MovieSortOrder(SortField field, boolean descending) {

  /** The field a search is primarily ordered by. */
  public enum SortField {
    TITLE("title"),
    RELEASE_YEAR("releaseYear"),
    RATING("rating");

    private final String publishedName;

    SortField(String publishedName) {
      this.publishedName = publishedName;
    }

    String publishedName() {
      return publishedName;
    }
  }

  public static final MovieSortOrder DEFAULT = new MovieSortOrder(SortField.RELEASE_YEAR, true);

  private static final String PARAMETER = "sort";

  public MovieSortOrder {
    Objects.requireNonNull(field, "field must not be null");
  }

  /**
   * @throws InvalidRequestException naming {@code sort} when the value is not one of the six
   *     supported values
   */
  public static MovieSortOrder parse(Optional<String> value) {
    if (value.isEmpty()) {
      return DEFAULT;
    }
    String raw = value.get();
    boolean descending = raw.startsWith("-");
    String name = descending ? raw.substring(1) : raw;
    for (SortField field : SortField.values()) {
      if (field.publishedName().equals(name)) {
        return new MovieSortOrder(field, descending);
      }
    }
    throw new InvalidRequestException(PARAMETER);
  }
}
