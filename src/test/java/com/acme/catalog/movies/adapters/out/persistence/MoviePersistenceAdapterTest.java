package com.acme.catalog.movies.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.common.test.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Testcontainers (real Postgres, no H2) test for {@link MoviePersistenceAdapter}. Inserts its own
 * fixtures — never relies on the demo seed.
 */
@Transactional
class MoviePersistenceAdapterTest extends PostgresIntegrationTest {

  @Autowired private MovieJpaRepository movieJpaRepository;
  @Autowired private GenreJpaRepository genreJpaRepository;
  @Autowired private MoviePersistenceAdapter moviePersistenceAdapter;

  @Test
  void load_returnsTheMovieWithAllFieldsMapped_whenAllOptionalsPresent() {
    UUID movieId = UUID.randomUUID();
    saveMovie(
        movieId,
        "The Matrix",
        1999,
        137,
        "A hacker discovers reality is a simulation.",
        BigDecimal.valueOf(4.5),
        Set.of("Sci-Fi", "Action"));

    Optional<Movie> result = moviePersistenceAdapter.load(new MovieId(movieId));

    assertThat(result).isPresent();
    Movie movie = result.orElseThrow();
    assertThat(movie.id()).isEqualTo(new MovieId(movieId));
    assertThat(movie.title()).isEqualTo("The Matrix");
    assertThat(movie.releaseYear()).isEqualTo(1999);
    assertThat(movie.genres()).extracting("label").containsExactlyInAnyOrder("Sci-Fi", "Action");
    assertThat(movie.runtimeMinutes()).contains(137);
    assertThat(movie.synopsis()).contains("A hacker discovers reality is a simulation.");
    assertThat(movie.rating()).isPresent();
    assertThat(movie.rating().orElseThrow().score()).isEqualByComparingTo("4.5");
  }

  @Test
  void load_returnsTheMovieWithOptionalsAbsent_whenNotSet() {
    UUID movieId = UUID.randomUUID();
    saveMovie(movieId, "Obscure Film", 2001, null, null, null, Set.of("Drama"));

    Optional<Movie> result = moviePersistenceAdapter.load(new MovieId(movieId));

    assertThat(result).isPresent();
    Movie movie = result.orElseThrow();
    assertThat(movie.runtimeMinutes()).isEmpty();
    assertThat(movie.synopsis()).isEmpty();
    assertThat(movie.rating()).isEmpty();
    assertThat(movie.genres()).extracting("label").containsExactly("Drama");
  }

  @Test
  void load_returnsEmpty_whenNoMovieForId() {
    Optional<Movie> result = moviePersistenceAdapter.load(new MovieId(UUID.randomUUID()));

    assertThat(result).isEmpty();
  }

  private void saveMovie(
      UUID movieId,
      String title,
      int releaseYear,
      Integer runtimeMinutes,
      String synopsis,
      BigDecimal rating,
      Set<String> genreNames) {
    Set<GenreJpaEntity> genres = new LinkedHashSet<>();
    for (String name : genreNames) {
      genres.add(genreJpaRepository.save(new GenreJpaEntity(UUID.randomUUID(), name)));
    }
    MovieJpaEntity entity =
        new MovieJpaEntity(movieId, title, releaseYear, runtimeMinutes, synopsis, rating, genres);
    movieJpaRepository.save(entity);
  }
}
