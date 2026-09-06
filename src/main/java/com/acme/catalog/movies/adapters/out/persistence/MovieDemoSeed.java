package com.acme.catalog.movies.adapters.out.persistence;

import com.acme.catalog.movies.domain.model.Genre;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Idempotent demo-profile seed so the zero-dependency H2 default runtime serves demonstrable movie
 * detail. Active whenever the {@code test} profile is NOT active, so the test suite (which always
 * activates {@code test} — see {@link com.acme.common.test.PostgresIntegrationTest}) never runs it
 * and stays seed-independent with its own fixtures. Guarded by an emptiness check so restarts never
 * duplicate rows.
 */
@Component
@Profile("!test")
public class MovieDemoSeed implements CommandLineRunner {

  /** Fixed identifiers so the demo runtime's detail links are stable across restarts. */
  static final UUID FULL_DETAIL_MOVIE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  static final UUID MINIMAL_DETAIL_MOVIE_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");

  private final MovieJpaRepository movieJpaRepository;

  public MovieDemoSeed(MovieJpaRepository movieJpaRepository) {
    this.movieJpaRepository = movieJpaRepository;
  }

  @Override
  public void run(String... args) {
    if (movieJpaRepository.count() > 0) {
      return;
    }

    movieJpaRepository.save(
        new MovieJpaEntity(
            FULL_DETAIL_MOVIE_ID,
            "The Wandering Reel",
            2019,
            118,
            "A projectionist discovers a film that predicts the next day's news.",
            BigDecimal.valueOf(4.5),
            Set.of(Genre.DRAMA)));

    movieJpaRepository.save(
        new MovieJpaEntity(
            MINIMAL_DETAIL_MOVIE_ID,
            "Silent Harbor",
            2021,
            null,
            null,
            null,
            Set.of(Genre.MYSTERY)));
  }
}
