package com.acme.catalog.movies.adapters.out.persistence;

/**
 * Escapes SQL {@code LIKE} wildcard characters ({@code \}, {@code %}, {@code _}) in a
 * consumer-supplied term so they are matched literally, then wraps the escaped term as a {@code
 * %term%} substring pattern. Pair with {@code ESCAPE '\'} in the SQL {@code LIKE} clause.
 */
final class LikePatternEscaper {

  private LikePatternEscaper() {}

  static String substringPattern(String term) {
    String escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped + "%";
  }
}
