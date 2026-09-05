package com.acme.catalog.movies.adapters.out.persistence;

import com.acme.catalog.movies.application.port.out.SearchMoviesPort;
import com.acme.catalog.movies.domain.model.Genre;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MoviePage;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSort;
import com.acme.catalog.movies.domain.model.MovieSortField;
import com.acme.catalog.movies.domain.model.SortDirection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implements {@link SearchMoviesPort} against Postgres via a two-step id-page-then-fetch query (see
 * {@code openspec/changes/add-movie-search/design.md}, D2): step 1 selects a filtered, sorted page
 * of movie ids (SQL {@code LIMIT}/{@code OFFSET}, no genre fetch — avoids Hibernate in-memory
 * pagination); step 2 fetches the full entities for that bounded id set with a {@code LEFT JOIN
 * FETCH} on genres (no pagination — avoids N+1) and re-orders them to the step-1 id order. {@code
 * totalElements} wraps the identical filtered/grouped id-selection in a derived table so counts
 * stay correct when the genre AND-filter's {@code GROUP BY ... HAVING} is active. Does not change
 * {@link MovieJpaEntity}'s mapping or the CAT-001 detail path.
 */
@Component
public class MovieSearchPersistenceAdapter implements SearchMoviesPort {

  @PersistenceContext private EntityManager entityManager;

  @Override
  public MoviePage search(MovieSearchCriteria criteria, int page, int size, MovieSort sort) {
    Map<String, Object> params = new HashMap<>();
    String whereClause = buildWhereClause(criteria, params);

    long totalElements = countMatchingIds(whereClause, params);
    int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;

    List<UUID> pageIds = selectPageIds(whereClause, params, sort, page, size);
    List<Movie> items = fetchInIdOrder(pageIds);

    return new MoviePage(items, page, size, totalElements, totalPages);
  }

  private long countMatchingIds(String whereClause, Map<String, Object> params) {
    String sql =
        "SELECT COUNT(*) FROM (SELECT m.id FROM movies m WHERE " + whereClause + ") AS sub";
    Query query = entityManager.createNativeQuery(sql);
    bindParams(query, params);
    Number count = (Number) query.getSingleResult();
    return count.longValue();
  }

  private List<UUID> selectPageIds(
      String whereClause, Map<String, Object> params, MovieSort sort, int page, int size) {
    // Id is projected as CAST(... AS varchar) rather than the raw uuid column: Postgres returns a
    // java.sql.UUID for a uuid column, but H2 (even in MODE=PostgreSQL) returns byte[], which
    // cannot be cast to UUID. Casting to varchar in SQL and parsing with UUID.fromString(...) is
    // identical on both engines and does not affect the ORDER BY (which still sorts on the real
    // m.id column) or the bounded statement count.
    String sql =
        "SELECT CAST(m.id AS varchar) FROM movies m WHERE "
            + whereClause
            + " "
            + orderByClause(sort)
            + " LIMIT :limit OFFSET :offset";
    Query query = entityManager.createNativeQuery(sql);
    bindParams(query, params);
    query.setParameter("limit", size);
    query.setParameter("offset", (long) page * size);

    @SuppressWarnings("unchecked")
    List<String> idStrings = query.getResultList();
    return idStrings.stream().map(UUID::fromString).toList();
  }

  private List<Movie> fetchInIdOrder(List<UUID> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    List<MovieJpaEntity> entities =
        entityManager
            .createQuery(
                "SELECT DISTINCT m FROM MovieJpaEntity m LEFT JOIN FETCH m.genres WHERE m.id IN"
                    + " :ids",
                MovieJpaEntity.class)
            .setParameter("ids", ids)
            .getResultList();

    Map<UUID, MovieJpaEntity> byId = new LinkedHashMap<>();
    for (MovieJpaEntity entity : entities) {
      byId.put(entity.getId(), entity);
    }

    List<Movie> ordered = new ArrayList<>(ids.size());
    for (UUID id : ids) {
      MovieJpaEntity entity = byId.get(id);
      if (entity != null) {
        ordered.add(MoviePersistenceAdapter.toDomain(entity));
      }
    }
    return ordered;
  }

  /**
   * Builds the shared, parameterised {@code WHERE} predicate (title/year/minRating/genre-AND) used
   * identically by both the id-page query and the wrapped count query, registering bound parameter
   * values into {@code params}. The genre AND-filter restricts to the requested genres *before*
   * grouping ({@code WHERE g.name IN (...) GROUP BY movie_id HAVING COUNT(DISTINCT g.name) =
   * :genreCount}) — omitting that restriction would count a movie's *total* genres instead of how
   * many of the *requested* genres it carries.
   */
  private static String buildWhereClause(MovieSearchCriteria criteria, Map<String, Object> params) {
    params.put("title", criteria.title().orElse(null));
    params.put(
        "titlePattern",
        criteria.title().map(t -> "%" + escapeLike(t.toLowerCase(Locale.ROOT)) + "%").orElse(null));
    params.put("yearFrom", criteria.yearFrom().orElse(null));
    params.put("yearTo", criteria.yearTo().orElse(null));
    params.put("minRating", criteria.minRating().map(r -> r.score()).orElse(null));

    // Every bound parameter is CAST so Postgres can determine its type even when the value is
    // null (an untyped null parameter otherwise fails with "could not determine data type of
    // parameter"); a null-cast IS NULL check safely short-circuits the rest of its OR clause.
    // The LIKE pattern is escaped (see escapeLike) and paired with ESCAPE '\' so a literal
    // '%'/'_'/'\' in the search term is matched literally, not as a wildcard.
    StringBuilder where = new StringBuilder();
    where
        .append(
            "(CAST(:title AS text) IS NULL OR LOWER(m.title) LIKE CAST(:titlePattern AS text)"
                + " ESCAPE '\\')")
        .append(
            " AND (CAST(:yearFrom AS integer) IS NULL OR m.release_year >= CAST(:yearFrom AS"
                + " integer))")
        .append(
            " AND (CAST(:yearTo AS integer) IS NULL OR m.release_year <= CAST(:yearTo AS"
                + " integer))")
        .append(
            " AND (CAST(:minRating AS numeric) IS NULL OR m.rating >= CAST(:minRating AS"
                + " numeric))");

    // De-duplicated: a repeated genre value (e.g. genre=Drama&genre=Drama) must behave like a
    // single genre, not push genreCount past the number of distinct genres the movie can ever
    // carry (which would make the filter permanently unsatisfiable).
    Set<String> distinctGenreLabels = new LinkedHashSet<>();
    for (Genre genre : criteria.genres()) {
      distinctGenreLabels.add(genre.label());
    }
    if (!distinctGenreLabels.isEmpty()) {
      List<String> genreParamNames = new ArrayList<>(distinctGenreLabels.size());
      int i = 0;
      for (String label : distinctGenreLabels) {
        String paramName = "genre" + i;
        genreParamNames.add(":" + paramName);
        params.put(paramName, label);
        i++;
      }
      params.put("genreCount", (long) distinctGenreLabels.size());
      where
          .append(" AND m.id IN (SELECT mg.movie_id FROM movie_genre mg")
          .append(" JOIN genres g ON g.id = mg.genre_id")
          .append(" WHERE g.name IN (")
          .append(String.join(", ", genreParamNames))
          .append(") GROUP BY mg.movie_id HAVING COUNT(DISTINCT g.name) = :genreCount)");
    }

    return where.toString();
  }

  /**
   * Escapes LIKE metacharacters ({@code \}, {@code %}, {@code _}) in a user-supplied search term so
   * it is matched literally when wrapped in {@code %...%} and paired with {@code ESCAPE '\'}. The
   * backslash itself must be escaped first, or escaping it after {@code %}/{@code _} would
   * double-escape the backslashes just introduced.
   */
  private static String escapeLike(String term) {
    return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static void bindParams(Query query, Map<String, Object> params) {
    params.forEach(query::setParameter);
  }

  /**
   * Orders results in SQL: the requested field/direction, a {@code title} ascending tiebreak, then
   * {@code m.id} ascending as a final, unique terminal key. A {@code rating} sort uses {@code NULLS
   * LAST} in both directions, so unrated movies always sort after rated ones regardless of
   * direction. The {@code m.id} tiebreak is mandatory for the id-page query: {@code (sort field,
   * title)} alone is not unique — two rows with the same sort field and title would otherwise have
   * an undefined relative order under {@code LIMIT}/{@code OFFSET}, letting one row be skipped and
   * another repeated across pages.
   */
  private static String orderByClause(MovieSort sort) {
    String direction = sort.direction() == SortDirection.DESC ? "DESC" : "ASC";
    String column = sortColumn(sort.field());

    StringBuilder orderBy =
        new StringBuilder("ORDER BY ").append(column).append(' ').append(direction);
    if (sort.field() == MovieSortField.RATING) {
      orderBy.append(" NULLS LAST");
    }
    orderBy.append(", m.title ASC, m.id ASC");
    return orderBy.toString();
  }

  private static String sortColumn(MovieSortField field) {
    return switch (field) {
      case TITLE -> "m.title";
      case RELEASE_YEAR -> "m.release_year";
      case RATING -> "m.rating";
    };
  }
}
