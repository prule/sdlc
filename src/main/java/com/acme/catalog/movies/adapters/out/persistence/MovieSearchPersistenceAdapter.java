package com.acme.catalog.movies.adapters.out.persistence;

import com.acme.catalog.movies.application.port.out.SearchMoviesPort;
import com.acme.catalog.movies.domain.model.Genre;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MoviePage;
import com.acme.catalog.movies.domain.model.MoviePageRequest;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSort;
import com.acme.catalog.movies.domain.model.Rating;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implements {@link SearchMoviesPort} with a bounded, page-size-independent (non-N+1) two-phase
 * read: (1) a native-SQL query selects the page of matching movie ids with all filters and full
 * ordering applied, (2) a matching count query fills {@code totalElements}, then (3) exactly those
 * ids are hydrated with their genres in a single fetch-join query, reordered to preserve the
 * phase-1 order. All three statements run through the same JPA {@link EntityManager} (Hibernate
 * session) so a Hibernate-statistics guard test can assert the exact, page-size-independent
 * statement count. See {@code openspec/changes/add-movie-search/design.md} for the rationale (a
 * single fetch-join page over a to-many collection paginates in-memory and breaks correct paging).
 *
 * <p>The native id query projects the {@code uuid} id column as {@code varchar} and parses it with
 * {@link UUID#fromString}: casting the JDBC result straight to {@link UUID} works on Postgres but
 * throws {@code ClassCastException} on the H2 default runtime (see CLAUDE.md).
 */
@Component
public class MovieSearchPersistenceAdapter implements SearchMoviesPort {

  private final EntityManager entityManager;
  private final MovieJpaRepository movieJpaRepository;

  public MovieSearchPersistenceAdapter(
      EntityManager entityManager, MovieJpaRepository movieJpaRepository) {
    this.entityManager = entityManager;
    this.movieJpaRepository = movieJpaRepository;
  }

  @Override
  public MoviePage search(
      MovieSearchCriteria criteria, MoviePageRequest pageRequest, MovieSort sort) {
    Map<String, Object> params = new LinkedHashMap<>();
    String whereClause = buildWhereClause(criteria, params);

    List<UUID> pageIds = selectPageIds(whereClause, params, sort, pageRequest);
    long totalElements = countMatching(whereClause, params);

    List<Movie> content = pageIds.isEmpty() ? List.of() : hydrateInOrder(pageIds);

    return new MoviePage(content, pageRequest.page(), pageRequest.size(), totalElements);
  }

  /**
   * Builds the shared {@code WHERE} predicate (and collects its bind parameters) applied
   * identically by both the phase-1 id-selection query and the phase-2 count query, so {@code
   * totalElements} can never disagree with the rows actually returned.
   */
  private static String buildWhereClause(MovieSearchCriteria criteria, Map<String, Object> params) {
    StringBuilder where = new StringBuilder("1=1");

    criteria
        .title()
        .ifPresent(
            title -> {
              where.append(" AND lower(m.title) LIKE lower(:titlePattern) ESCAPE '\\'");
              params.put("titlePattern", LikePatternEscaper.substringPattern(title));
            });

    if (!criteria.genres().isEmpty()) {
      // Restrict-then-count construct: WHERE genre IN (:genres) GROUP BY movie_id HAVING
      // COUNT(DISTINCT genre) = :genreCount. Restricting to the *supplied* genres before grouping
      // means a movie carrying a superset of the supplied genres still matches — a bare
      // HAVING COUNT(DISTINCT genre) = :n over the unrestricted join would count the movie's
      // TOTAL distinct genres and wrongly reject supersets. Do not "simplify" this away.
      where.append(
          " AND m.id IN (SELECT mg.movie_id FROM movie_genres mg WHERE mg.genre IN (:genres)"
              + " GROUP BY mg.movie_id HAVING COUNT(DISTINCT mg.genre) = :genreCount)");
      params.put("genres", criteria.genres().stream().map(Genre::name).toList());
      params.put("genreCount", criteria.genres().size());
    }

    // An inverted range (releaseYearFrom > releaseYearTo) is a valid request that simply matches
    // nothing — do not add a bounds check that rejects it as a 400.
    criteria
        .releaseYearFrom()
        .ifPresent(
            from -> {
              where.append(" AND m.release_year >= :releaseYearFrom");
              params.put("releaseYearFrom", from);
            });
    criteria
        .releaseYearTo()
        .ifPresent(
            to -> {
              where.append(" AND m.release_year <= :releaseYearTo");
              params.put("releaseYearTo", to);
            });

    criteria
        .minRating()
        .ifPresent(
            minRating -> {
              where.append(" AND m.rating IS NOT NULL AND m.rating >= :minRating");
              params.put("minRating", minRating);
            });

    return where.toString();
  }

  @SuppressWarnings("unchecked")
  private List<UUID> selectPageIds(
      String whereClause,
      Map<String, Object> params,
      MovieSort sort,
      MoviePageRequest pageRequest) {
    String sql =
        "SELECT CAST(m.id AS varchar) AS id FROM movies m WHERE "
            + whereClause
            + " ORDER BY "
            + orderByClause(sort)
            + " LIMIT :limit OFFSET :offset";

    Query query = entityManager.createNativeQuery(sql);
    bindParams(query, params);
    query.setParameter("limit", pageRequest.size());
    query.setParameter("offset", (long) pageRequest.page() * (long) pageRequest.size());

    List<String> idStrings = query.getResultList();
    return idStrings.stream().map(UUID::fromString).toList();
  }

  private long countMatching(String whereClause, Map<String, Object> params) {
    String sql = "SELECT COUNT(DISTINCT m.id) FROM movies m WHERE " + whereClause;
    Query query = entityManager.createNativeQuery(sql);
    bindParams(query, params);

    Number count = (Number) query.getSingleResult();
    return count == null ? 0L : count.longValue();
  }

  private static void bindParams(Query query, Map<String, Object> params) {
    params.forEach(query::setParameter);
  }

  /**
   * The requested/default field+direction, then title ascending as a secondary tiebreak, then id
   * ascending as the terminal unique tiebreak — making the total order strict so offset paging
   * never skips or duplicates rows that tie on the sort field.
   */
  private static String orderByClause(MovieSort sort) {
    String column =
        switch (sort.field()) {
          case TITLE -> "m.title";
          case RELEASE_YEAR -> "m.release_year";
          case RATING -> "m.rating";
        };
    String direction = sort.direction() == MovieSort.Direction.ASC ? "ASC" : "DESC";
    return column + " " + direction + ", m.title ASC, m.id ASC";
  }

  /** Hydrates the given ids with their genres in one fetch-join query, preserving their order. */
  private List<Movie> hydrateInOrder(List<UUID> orderedIds) {
    List<MovieJpaEntity> entities = movieJpaRepository.findAllWithGenresByIdIn(orderedIds);
    Map<UUID, MovieJpaEntity> byId = new LinkedHashMap<>();
    for (MovieJpaEntity entity : entities) {
      byId.put(entity.getId(), entity);
    }
    return orderedIds.stream()
        .map(byId::get)
        .filter(Objects::nonNull)
        .map(MovieSearchPersistenceAdapter::toDomain)
        .toList();
  }

  private static Movie toDomain(MovieJpaEntity entity) {
    return Movie.of(
        entity.getId(),
        entity.getTitle(),
        entity.getReleaseYear(),
        List.copyOf(entity.getGenres()),
        entity.getRuntimeMinutes(),
        entity.getSynopsis(),
        entity.getRating() == null ? null : new Rating(entity.getRating()));
  }
}
