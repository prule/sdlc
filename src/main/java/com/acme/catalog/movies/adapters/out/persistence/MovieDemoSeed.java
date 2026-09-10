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

  /**
   * Movie with both cast and crew, including a no-character cast credit and out-of-order billing.
   */
  static final UUID CREDITED_MOVIE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

  /** Existing movie with no cast or crew recorded — proves the 200-empty path. */
  static final UUID CREDITLESS_MOVIE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

  private static final UUID LEAD_ACTOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private static final UUID SUPPORTING_ACTOR_ID =
      UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  private static final UUID DIRECTOR_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
  private static final UUID COMPOSER_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

  private final MovieJpaRepository movieJpaRepository;
  private final PersonJpaRepository personJpaRepository;
  private final CreditJpaRepository creditJpaRepository;

  public MovieDemoSeed(
      MovieJpaRepository movieJpaRepository,
      PersonJpaRepository personJpaRepository,
      CreditJpaRepository creditJpaRepository) {
    this.movieJpaRepository = movieJpaRepository;
    this.personJpaRepository = personJpaRepository;
    this.creditJpaRepository = creditJpaRepository;
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

    movieJpaRepository.save(
        new MovieJpaEntity(
            CREDITED_MOVIE_ID,
            "Harbor Lights",
            2020,
            105,
            "A small-town harbor hides a bigger secret.",
            BigDecimal.valueOf(4.0),
            Set.of(Genre.DRAMA)));

    movieJpaRepository.save(
        new MovieJpaEntity(
            CREDITLESS_MOVIE_ID, "Uncredited", 2022, null, null, null, Set.of(Genre.DOCUMENTARY)));

    seedCredits();
  }

  private void seedCredits() {
    PersonJpaEntity leadActor =
        personJpaRepository.save(new PersonJpaEntity(LEAD_ACTOR_ID, "Ava Solano"));
    PersonJpaEntity supportingActor =
        personJpaRepository.save(new PersonJpaEntity(SUPPORTING_ACTOR_ID, "Marcus Reyes"));
    PersonJpaEntity director =
        personJpaRepository.save(new PersonJpaEntity(DIRECTOR_ID, "Priya Nandan"));
    PersonJpaEntity composer =
        personJpaRepository.save(new PersonJpaEntity(COMPOSER_ID, "Tomas Berg"));

    // Out-of-order billing on purpose (2 saved before 1) to prove ordering is not query-order
    // reliant; the supporting actor has no recorded character to prove omission.
    creditJpaRepository.save(
        CreditJpaEntity.cast(UUID.randomUUID(), CREDITED_MOVIE_ID, supportingActor, null, 2));
    creditJpaRepository.save(
        CreditJpaEntity.cast(UUID.randomUUID(), CREDITED_MOVIE_ID, leadActor, "Dana Whitfield", 1));

    creditJpaRepository.save(
        CreditJpaEntity.crew(UUID.randomUUID(), CREDITED_MOVIE_ID, composer, "Sound", "Composer"));
    creditJpaRepository.save(
        CreditJpaEntity.crew(
            UUID.randomUUID(), CREDITED_MOVIE_ID, director, "Directing", "Director"));
  }
}
