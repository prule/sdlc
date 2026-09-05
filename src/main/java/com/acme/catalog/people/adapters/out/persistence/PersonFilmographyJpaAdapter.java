package com.acme.catalog.people.adapters.out.persistence;

import com.acme.catalog.movies.adapters.out.persistence.MovieJpaEntity;
import com.acme.catalog.movies.domain.model.Genre;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.catalog.movies.domain.model.Rating;
import com.acme.catalog.people.application.port.out.PersonFilmographyPort;
import com.acme.catalog.people.domain.model.ActingCapacity;
import com.acme.catalog.people.domain.model.FilmographyCapacity;
import com.acme.catalog.people.domain.model.FilmographyEntry;
import com.acme.catalog.people.domain.model.FilmographyPage;
import com.acme.catalog.people.domain.model.NonActingCapacity;
import com.acme.catalog.people.domain.model.PersonId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implements {@link PersonFilmographyPort} against Postgres via a bounded, id-page-then-fetch query
 * (see {@code openspec/changes/add-person-filmography/design.md}, D-B): an existence probe (reusing
 * the {@code catalog/people} side's {@link PersonJpaRepository} — no third Person entity/repo, the
 * CAT-004 lesson), a {@code COUNT} for {@code totalElements}, a single native query selecting an
 * ordered, {@code LIMIT}/{@code OFFSET} page of {@code credits} rows (joined to {@code movies} only
 * for the sort keys — the credit row already carries the capacity fields, so no per-item capacity
 * fetch), and a single fetch-join query for that page's Movie summaries (with genres, avoiding
 * N+1). Four bounded statements regardless of filmography size. The domain is never annotated
 * {@code @Entity}.
 */
@Component
public class PersonFilmographyJpaAdapter implements PersonFilmographyPort {

  private final PersonJpaRepository personJpaRepository;

  @PersistenceContext private EntityManager entityManager;

  public PersonFilmographyJpaAdapter(PersonJpaRepository personJpaRepository) {
    this.personJpaRepository = personJpaRepository;
  }

  @Override
  public Optional<FilmographyPage> loadFilmography(PersonId id, int page, int size) {
    if (!personJpaRepository.existsById(id.value())) {
      return Optional.empty();
    }

    long totalElements = countCredits(id.value());
    int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;

    List<CreditRow> creditRows = selectCreditRowsPage(id.value(), page, size);
    Map<UUID, MovieJpaEntity> moviesById = fetchMoviesById(creditRows);

    List<FilmographyEntry> items = new ArrayList<>(creditRows.size());
    for (CreditRow row : creditRows) {
      MovieJpaEntity movie = moviesById.get(row.movieId());
      if (movie != null) {
        items.add(toEntry(row, movie));
      }
    }

    return Optional.of(new FilmographyPage(items, page, size, totalElements, totalPages));
  }

  private long countCredits(UUID personId) {
    Query query =
        entityManager.createNativeQuery("SELECT COUNT(*) FROM credits WHERE person_id = :personId");
    query.setParameter("personId", personId);
    return ((Number) query.getSingleResult()).longValue();
  }

  /**
   * Orders by {@code m.release_year DESC, m.title ASC, c.id ASC} — the credit id is a unique
   * terminal tiebreak (see design.md, D-C), which is mandatory here: {@code (releaseYear, title)}
   * alone is not unique, so without it a tied pair's relative order under {@code LIMIT}/{@code
   * OFFSET} would be undefined, letting one row be skipped and another repeated across pages.
   */
  private List<CreditRow> selectCreditRowsPage(UUID personId, int page, int size) {
    String sql =
        "SELECT c.id, c.movie_id, c.credit_type, c.character_name, c.billing_order, c.department,"
            + " c.job FROM credits c JOIN movies m ON m.id = c.movie_id WHERE c.person_id ="
            + " :personId ORDER BY m.release_year DESC, m.title ASC, c.id ASC LIMIT :limit OFFSET"
            + " :offset";
    Query query = entityManager.createNativeQuery(sql);
    query.setParameter("personId", personId);
    query.setParameter("limit", size);
    query.setParameter("offset", (long) page * size);

    @SuppressWarnings("unchecked")
    List<Object[]> rows = query.getResultList();
    List<CreditRow> result = new ArrayList<>(rows.size());
    for (Object[] row : rows) {
      result.add(
          new CreditRow(
              (UUID) row[0],
              (UUID) row[1],
              (String) row[2],
              (String) row[3],
              (Integer) row[4],
              (String) row[5],
              (String) row[6]));
    }
    return result;
  }

  private Map<UUID, MovieJpaEntity> fetchMoviesById(List<CreditRow> creditRows) {
    if (creditRows.isEmpty()) {
      return Map.of();
    }
    List<UUID> movieIds = creditRows.stream().map(CreditRow::movieId).distinct().toList();
    List<MovieJpaEntity> entities =
        entityManager
            .createQuery(
                "SELECT DISTINCT m FROM MovieJpaEntity m LEFT JOIN FETCH m.genres WHERE m.id IN"
                    + " :ids",
                MovieJpaEntity.class)
            .setParameter("ids", movieIds)
            .getResultList();

    Map<UUID, MovieJpaEntity> byId = new LinkedHashMap<>();
    for (MovieJpaEntity entity : entities) {
      byId.put(entity.getId(), entity);
    }
    return byId;
  }

  private static FilmographyEntry toEntry(CreditRow row, MovieJpaEntity movie) {
    List<Genre> genres = movie.getGenres().stream().map(g -> new Genre(g.getName())).toList();
    return new FilmographyEntry(
        new MovieId(movie.getId()),
        movie.getTitle(),
        movie.getReleaseYear(),
        genres,
        Optional.ofNullable(movie.getRuntimeMinutes()),
        Optional.ofNullable(movie.getRating()).map(Rating::new),
        toCapacity(row),
        row.id());
  }

  private static FilmographyCapacity toCapacity(CreditRow row) {
    return switch (row.creditType()) {
      case "CAST" -> new ActingCapacity(row.characterName(), row.billingOrder());
      case "CREW" -> new NonActingCapacity(row.department(), row.job());
      default -> throw new IllegalStateException("Unknown credit_type: " + row.creditType());
    };
  }

  private record CreditRow(
      UUID id,
      UUID movieId,
      String creditType,
      String characterName,
      Integer billingOrder,
      String department,
      String job) {}
}
