package com.acme.catalog.movies.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.testsupport.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Testcontainers coverage for {@link MoviePersistenceAdapter} against real PostgreSQL (design D5).
 */
class MoviePersistenceAdapterTest extends PostgresIntegrationTest {

  @Autowired private MoviePersistenceAdapter adapter;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update("DELETE FROM movie_genre");
    jdbcTemplate.update("DELETE FROM movie");
    jdbcTemplate.update("DELETE FROM genre");
  }

  private void insertGenre(UUID id, String name) {
    jdbcTemplate.update("INSERT INTO genre (id, name) VALUES (?, ?)", id, name);
  }

  private void insertMovie(
      UUID id,
      String title,
      int releaseYear,
      Integer runtimeMinutes,
      String synopsis,
      BigDecimal rating) {
    jdbcTemplate.update(
        "INSERT INTO movie (id, title, release_year, runtime_minutes, synopsis, rating) "
            + "VALUES (?, ?, ?, ?, ?, ?)",
        id,
        title,
        releaseYear,
        runtimeMinutes,
        synopsis,
        rating);
  }

  private void linkGenre(UUID movieId, UUID genreId) {
    jdbcTemplate.update(
        "INSERT INTO movie_genre (movie_id, genre_id) VALUES (?, ?)", movieId, genreId);
  }

  @Test
  void loadsAFullyPopulatedMovie() {
    UUID movieId = UUID.randomUUID();
    UUID dramaId = UUID.randomUUID();
    UUID sciFiId = UUID.randomUUID();
    insertGenre(dramaId, "Drama");
    insertGenre(sciFiId, "Sci-Fi");
    insertMovie(
        movieId,
        "Arrival",
        2016,
        116,
        "A linguist deciphers an alien language.",
        new BigDecimal("4.5"));
    linkGenre(movieId, dramaId);
    linkGenre(movieId, sciFiId);

    Optional<Movie> result = adapter.loadMovie(new MovieId(movieId));

    assertThat(result).isPresent();
    Movie movie = result.orElseThrow();
    assertThat(movie.id()).isEqualTo(new MovieId(movieId));
    assertThat(movie.title()).isEqualTo("Arrival");
    assertThat(movie.releaseYear()).isEqualTo(2016);
    assertThat(movie.genres()).containsExactly("Drama", "Sci-Fi");
    assertThat(movie.runtime()).isPresent().get().extracting("value").isEqualTo(116);
    assertThat(movie.synopsis()).contains("A linguist deciphers an alien language.");
    assertThat(movie.rating())
        .isPresent()
        .get()
        .extracting("value")
        .isEqualTo(new BigDecimal("4.5"));
  }

  @Test
  void loadsAMovieWithNoOptionalFields() {
    UUID movieId = UUID.randomUUID();
    insertMovie(movieId, "Untitled", 1999, null, null, null);

    Optional<Movie> result = adapter.loadMovie(new MovieId(movieId));

    assertThat(result).isPresent();
    Movie movie = result.orElseThrow();
    assertThat(movie.runtime()).isEmpty();
    assertThat(movie.synopsis()).isEmpty();
    assertThat(movie.rating()).isEmpty();
  }

  @Test
  void aBlankSynopsisMapsToAbsent() {
    UUID movieId = UUID.randomUUID();
    insertMovie(movieId, "Untitled", 1999, null, "   ", null);

    Optional<Movie> result = adapter.loadMovie(new MovieId(movieId));

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().synopsis()).isEmpty();
  }

  @Test
  void loadsAMovieWithNoGenres() {
    UUID movieId = UUID.randomUUID();
    insertMovie(movieId, "Untitled", 1999, null, null, null);

    Optional<Movie> result = adapter.loadMovie(new MovieId(movieId));

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().genres()).isEmpty();
  }

  @Test
  void genresLinkedOutOfOrderAreReturnedAlphabetically() {
    UUID movieId = UUID.randomUUID();
    UUID thrillerId = UUID.randomUUID();
    UUID dramaId = UUID.randomUUID();
    insertGenre(thrillerId, "Thriller");
    insertGenre(dramaId, "Drama");
    insertMovie(movieId, "Untitled", 1999, null, null, null);
    linkGenre(movieId, thrillerId);
    linkGenre(movieId, dramaId);

    Optional<Movie> result = adapter.loadMovie(new MovieId(movieId));

    assertThat(result.orElseThrow().genres()).containsExactly("Drama", "Thriller");
  }

  @Test
  void returnsEmptyForAnUnknownId() {
    Optional<Movie> result = adapter.loadMovie(new MovieId(UUID.randomUUID()));

    assertThat(result).isEmpty();
  }
}
