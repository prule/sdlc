package com.acme.catalog.movies.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.catalog.movies.domain.model.Genre;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.common.test.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Persistence adapter test against real Postgres (Testcontainers, never H2). Own fixtures,
 * independent of {@link MovieDemoSeed}.
 */
class MoviePersistenceAdapterTest extends PostgresIntegrationTest {

  @Autowired private MovieJpaRepository movieJpaRepository;

  @Autowired private MoviePersistenceAdapter adapter;

  @Test
  void loadById_seededMovieWithAllOptionalFields_returnsItMappedToDomain() {
    UUID id = UUID.randomUUID();
    movieJpaRepository.save(
        new MovieJpaEntity(
            id,
            "The Wandering Reel",
            2019,
            118,
            "A projectionist discovers a film that predicts the news.",
            BigDecimal.valueOf(4.5),
            Set.of(Genre.DRAMA, Genre.MYSTERY)));

    Optional<Movie> result = adapter.loadById(id);

    assertThat(result).isPresent();
    Movie movie = result.orElseThrow();
    assertThat(movie.id()).isEqualTo(id);
    assertThat(movie.title()).isEqualTo("The Wandering Reel");
    assertThat(movie.releaseYear()).isEqualTo(2019);
    assertThat(movie.genres()).containsExactlyInAnyOrder(Genre.DRAMA, Genre.MYSTERY);
    assertThat(movie.runtimeMinutes()).contains(118);
    assertThat(movie.synopsis()).isPresent();
    assertThat(movie.rating()).isPresent();
    assertThat(movie.rating().orElseThrow().value()).isEqualByComparingTo(BigDecimal.valueOf(4.5));
  }

  @Test
  void loadById_seededMovieWithNoOptionalFields_returnsItWithThemAbsent() {
    UUID id = UUID.randomUUID();
    movieJpaRepository.save(
        new MovieJpaEntity(id, "Silent Harbor", 2021, null, null, null, Set.of(Genre.MYSTERY)));

    Optional<Movie> result = adapter.loadById(id);

    assertThat(result).isPresent();
    Movie movie = result.orElseThrow();
    assertThat(movie.runtimeMinutes()).isEmpty();
    assertThat(movie.synopsis()).isEmpty();
    assertThat(movie.rating()).isEmpty();
  }

  @Test
  void loadById_unknownId_returnsEmpty() {
    Optional<Movie> result = adapter.loadById(UUID.randomUUID());

    assertThat(result).isEmpty();
  }
}
