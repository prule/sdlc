package com.acme.catalog.movies.domain.model;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The one home of the rule for presenting a movie's genres: de-duplicated and ordered
 * alphabetically by name, ignoring letter case, with a tie broken by the exact name (BR-4, A-SORT),
 * shared by {@link Movie} and {@link MovieSummary}.
 */
final class GenreNames {

  private GenreNames() {}

  static List<String> normalize(List<String> genres) {
    return genres.stream()
        .distinct()
        .sorted(
            Comparator.comparing((String name) -> name.toLowerCase(Locale.ROOT))
                .thenComparing(Comparator.naturalOrder()))
        .toList();
  }
}
