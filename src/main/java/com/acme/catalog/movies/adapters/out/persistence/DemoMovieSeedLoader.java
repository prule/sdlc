package com.acme.catalog.movies.adapters.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads a small, committed demo dataset ({@code demo-data/movies.json}) so the happy path is
 * demonstrable in a running app. Active only under the {@code demo} profile — never {@code prod} —
 * and NOT a Flyway data migration (Flyway runs in every environment, including tests, which would
 * leak demo rows into production and couple tests to the seed). Idempotent: each movie's id is
 * deterministically derived from its title, and a movie already present is left untouched.
 *
 * <p>Automated tests never enable the {@code demo} profile; they insert their own fixtures and
 * assert against those, independent of this seed's contents.
 */
@Component
@Profile("demo")
public class DemoMovieSeedLoader implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoMovieSeedLoader.class);
  private static final String DATA_FILE = "demo-data/movies.json";

  private final MovieJpaRepository movieJpaRepository;
  private final GenreJpaRepository genreJpaRepository;
  private final ObjectMapper objectMapper;

  public DemoMovieSeedLoader(
      MovieJpaRepository movieJpaRepository, GenreJpaRepository genreJpaRepository) {
    this.movieJpaRepository = movieJpaRepository;
    this.genreJpaRepository = genreJpaRepository;
    this.objectMapper = new ObjectMapper();
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    List<DemoMovie> demoMovies;
    try (InputStream in = new ClassPathResource(DATA_FILE).getInputStream()) {
      demoMovies =
          objectMapper.readValue(
              in,
              objectMapper.getTypeFactory().constructCollectionType(List.class, DemoMovie.class));
    }

    int inserted = 0;
    for (DemoMovie demoMovie : demoMovies) {
      UUID id = UUID.nameUUIDFromBytes(demoMovie.title().getBytes());
      if (movieJpaRepository.existsById(id)) {
        continue;
      }
      movieJpaRepository.save(toEntity(id, demoMovie));
      inserted++;
    }
    log.info(
        "Demo movie seed: {} movie(s) inserted (of {} in dataset)", inserted, demoMovies.size());
  }

  private MovieJpaEntity toEntity(UUID id, DemoMovie demoMovie) {
    Set<GenreJpaEntity> genres = new LinkedHashSet<>();
    for (String genreName : demoMovie.genres()) {
      genres.add(findOrCreateGenre(genreName));
    }
    BigDecimal rating = demoMovie.rating() == null ? null : BigDecimal.valueOf(demoMovie.rating());
    return new MovieJpaEntity(
        id,
        demoMovie.title(),
        demoMovie.releaseYear(),
        demoMovie.runtimeMinutes(),
        demoMovie.synopsis(),
        rating,
        genres);
  }

  private GenreJpaEntity findOrCreateGenre(String name) {
    UUID id = UUID.nameUUIDFromBytes(("genre-" + name).getBytes());
    return genreJpaRepository
        .findById(id)
        .orElseGet(() -> genreJpaRepository.save(new GenreJpaEntity(id, name)));
  }

  @JsonDeserialize
  private record DemoMovie(
      String title,
      Integer releaseYear,
      List<String> genres,
      Integer runtimeMinutes,
      String synopsis,
      Double rating) {}
}
