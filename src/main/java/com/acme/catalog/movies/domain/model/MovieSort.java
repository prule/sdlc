package com.acme.catalog.movies.domain.model;

import java.util.Locale;
import java.util.Objects;

/**
 * A consumer-chosen sort field and direction for the movie search/browse operation. Parsed from the
 * wire format {@code field,direction} (e.g. {@code releaseYear,desc}). No Spring/JPA imports — see
 * standards/clean-architecture.md.
 */
public record MovieSort(MovieSort.Field field, MovieSort.Direction direction) {

  public MovieSort {
    Objects.requireNonNull(field, "field must not be null");
    Objects.requireNonNull(direction, "direction must not be null");
  }

  /** The default ordering when the consumer requests none: release year, descending. */
  public static MovieSort defaultSort() {
    return new MovieSort(Field.RELEASE_YEAR, Direction.DESC);
  }

  /**
   * Parses the wire format {@code field,direction} (e.g. {@code title,asc}), case-insensitive.
   *
   * @throws IllegalArgumentException if the value is malformed or names an unsupported field or
   *     direction
   */
  public static MovieSort parse(String value) {
    if (value == null || value.isBlank()) {
      return defaultSort();
    }
    String[] parts = value.split(",", -1);
    if (parts.length != 2) {
      throw new IllegalArgumentException("sort must be in the form 'field,direction': " + value);
    }
    Field field = Field.fromWireValue(parts[0].trim());
    Direction direction = Direction.fromWireValue(parts[1].trim());
    return new MovieSort(field, direction);
  }

  /** Supported sort fields. */
  public enum Field {
    TITLE("title"),
    RELEASE_YEAR("releaseYear"),
    RATING("rating");

    private final String wireValue;

    Field(String wireValue) {
      this.wireValue = wireValue;
    }

    static Field fromWireValue(String wireValue) {
      for (Field candidate : values()) {
        if (candidate.wireValue.equalsIgnoreCase(wireValue)) {
          return candidate;
        }
      }
      throw new IllegalArgumentException("unsupported sort field: " + wireValue);
    }
  }

  /** Sort direction. */
  public enum Direction {
    ASC,
    DESC;

    static Direction fromWireValue(String wireValue) {
      for (Direction candidate : values()) {
        if (candidate.name().equalsIgnoreCase(wireValue)) {
          return candidate;
        }
      }
      throw new IllegalArgumentException(
          "unsupported sort direction: " + wireValue.toLowerCase(Locale.ROOT));
    }
  }
}
