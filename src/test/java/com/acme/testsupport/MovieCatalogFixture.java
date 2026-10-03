package com.acme.testsupport;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Inserts curated catalog rows directly (there is no write path in the service) and clears them
 * again. Genres are created on first use by name.
 */
public final class MovieCatalogFixture {

  private final JdbcTemplate jdbcTemplate;
  private final Map<String, UUID> genreIds = new HashMap<>();

  public MovieCatalogFixture(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID genre(String name) {
    return genreIds.computeIfAbsent(
        name,
        n -> {
          UUID id = UUID.randomUUID();
          jdbcTemplate.update("INSERT INTO genre (id, name) VALUES (?, ?)", id, n);
          return id;
        });
  }

  public UUID movie(String title, int releaseYear, String rating, String... genres) {
    return movieWithDetails(title, releaseYear, null, null, rating, genres);
  }

  public UUID movieWithDetails(
      String title,
      int releaseYear,
      Integer runtimeMinutes,
      String synopsis,
      String rating,
      String... genres) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO movie (id, title, release_year, runtime_minutes, synopsis, rating) "
            + "VALUES (?, ?, ?, ?, ?, ?)",
        id,
        title,
        releaseYear,
        runtimeMinutes,
        synopsis,
        rating == null ? null : new BigDecimal(rating));
    for (String genre : genres) {
      jdbcTemplate.update(
          "INSERT INTO movie_genre (movie_id, genre_id) VALUES (?, ?)", id, genre(genre));
    }
    return id;
  }

  public void clear() {
    jdbcTemplate.update("DELETE FROM movie_genre");
    jdbcTemplate.update("DELETE FROM movie");
    jdbcTemplate.update("DELETE FROM genre");
    genreIds.clear();
  }
}
