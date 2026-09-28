package com.acme.catalog.movies.domain.model;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Normalizes a movie's genre names: de-duplicated and ordered alphabetically by name, ignoring
 * letter case, with a tie broken by the exact name (BR-4, A-SORT). Extracted from {@link Movie} so
 * {@link Movie} and {@link MovieSummary} sort genres identically (design D3, task 3.2).
 */
public final class GenreNames {

  private GenreNames() {}

  public static List<String> normalize(List<String> genres) {
    return genres.stream()
        .distinct()
        .sorted(
            Comparator.comparing((String name) -> name.toLowerCase(Locale.ROOT))
                .thenComparing(Comparator.naturalOrder()))
        .toList();
  }
}
