package com.acme.catalog.people.adapters.out.persistence;

import com.acme.catalog.people.application.port.out.LoadPersonFilmographyPort;
import com.acme.catalog.people.domain.model.Capacity;
import com.acme.catalog.people.domain.model.Filmography;
import com.acme.catalog.people.domain.model.FilmographyCriteria;
import com.acme.catalog.people.domain.model.FilmographyEntry;
import com.acme.catalog.people.domain.model.FilmographyMovieSummary;
import com.acme.catalog.people.domain.model.FilmographyPageRequest;
import com.acme.catalog.people.domain.model.Genre;
import com.acme.catalog.people.domain.model.Rating;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implements {@link LoadPersonFilmographyPort} with a bounded, page-size-independent two-phase read
 * (plus a hydration phase), mirroring {@code
 * com.acme.catalog.movies.adapters.out.persistence.MovieSearchPersistenceAdapter}: (1) a native-SQL
 * query selects the page of matching credit ids with all filters and full ordering applied, (2) a
 * matching count query (no {@code DISTINCT} — one credit row is already exactly one entry) fills
 * {@code totalElements}, then (3) exactly those credit ids are hydrated (movie fields, genres, and
 * capacity fields) in a single query and reordered to preserve the phase-1 order.
 *
 * <p>Deliberately does not import the {@code catalog.movies} slice's JPA entities/repositories
 * (design.md decision 3; the human-approved implementation constraint): all three phases run as
 * native SQL directly against the shared {@code credits}/{@code movies}/{@code movie_genres} tables
 * through this slice's own {@link EntityManager}, with lightweight local row-mapping only — no
 * cross-slice persistence dependency.
 *
 * <p>The native id query projects the {@code uuid} id columns as {@code varchar} and parses them
 * with {@link UUID#fromString}: casting the JDBC result straight to {@link UUID} works on Postgres
 * but throws {@code ClassCastException} on the H2 default runtime (see CLAUDE.md).
 */
@Component
public class PersonFilmographyJpaAdapter implements LoadPersonFilmographyPort {

  private final EntityManager entityManager;
  private final PersonDetailJpaRepository personDetailJpaRepository;

  public PersonFilmographyJpaAdapter(
      EntityManager entityManager, PersonDetailJpaRepository personDetailJpaRepository) {
    this.entityManager = entityManager;
    this.personDetailJpaRepository = personDetailJpaRepository;
  }

  @Override
  public Optional<Filmography> loadFilmography(
      UUID personId, FilmographyCriteria criteria, FilmographyPageRequest pageRequest) {
    if (!personDetailJpaRepository.existsById(personId)) {
      return Optional.empty();
    }

    Map<String, Object> params = new LinkedHashMap<>();
    params.put("personId", personId);
    String whereClause = buildWhereClause(criteria, params);

    List<UUID> pageCreditIds = selectPageCreditIds(whereClause, params, pageRequest);
    long totalElements = countMatching(whereClause, params);

    List<FilmographyEntry> content =
        pageCreditIds.isEmpty() ? List.of() : hydrateInOrder(pageCreditIds);

    return Optional.of(
        new Filmography(content, pageRequest.page(), pageRequest.size(), totalElements));
  }

  /**
   * Builds the shared {@code WHERE} predicate (and collects its bind parameters) applied
   * identically by both the phase-1 id-selection query and the phase-2 count query, so {@code
   * totalElements} can never disagree with the rows actually returned.
   */
  private static String buildWhereClause(FilmographyCriteria criteria, Map<String, Object> params) {
    StringBuilder where = new StringBuilder("c.person_id = :personId");

    criteria
        .capacity()
        .ifPresent(
            type -> {
              where.append(" AND c.kind = :kind");
              params.put("kind", type == Capacity.Type.ACTING ? "CAST" : "CREW");
            });

    // An inverted range (yearFrom > yearTo) is a valid request that simply matches nothing — do
    // not add a bounds check that rejects it as a 400.
    criteria
        .yearFrom()
        .ifPresent(
            from -> {
              where.append(" AND m.release_year >= :yearFrom");
              params.put("yearFrom", from);
            });
    criteria
        .yearTo()
        .ifPresent(
            to -> {
              where.append(" AND m.release_year <= :yearTo");
              params.put("yearTo", to);
            });

    return where.toString();
  }

  @SuppressWarnings("unchecked")
  private List<UUID> selectPageCreditIds(
      String whereClause, Map<String, Object> params, FilmographyPageRequest pageRequest) {
    String sql =
        "SELECT CAST(c.id AS varchar) AS id FROM credits c JOIN movies m ON m.id = c.movie_id"
            + " WHERE "
            + whereClause
            + " ORDER BY m.release_year DESC, m.title ASC, c.id ASC"
            + " LIMIT :limit OFFSET :offset";

    Query query = entityManager.createNativeQuery(sql);
    bindParams(query, params);
    query.setParameter("limit", pageRequest.size());
    query.setParameter("offset", (long) pageRequest.page() * (long) pageRequest.size());

    List<String> idStrings = query.getResultList();
    return idStrings.stream().map(UUID::fromString).toList();
  }

  private long countMatching(String whereClause, Map<String, Object> params) {
    String sql =
        "SELECT COUNT(*) FROM credits c JOIN movies m ON m.id = c.movie_id WHERE " + whereClause;
    Query query = entityManager.createNativeQuery(sql);
    bindParams(query, params);

    Number count = (Number) query.getSingleResult();
    return count == null ? 0L : count.longValue();
  }

  private static void bindParams(Query query, Map<String, Object> params) {
    params.forEach(query::setParameter);
  }

  /**
   * Hydrates exactly the given credit ids with their movie fields, genres, and capacity fields in
   * one query (avoiding an N+1 genre lookup per credit), then reorders the result to the phase-1
   * order. A movie carries potentially several genre rows, so this query returns one row per
   * (credit, genre) pair; rows sharing a credit id are grouped back into one entry.
   */
  @SuppressWarnings("unchecked")
  private List<FilmographyEntry> hydrateInOrder(List<UUID> orderedCreditIds) {
    String sql =
        "SELECT CAST(c.id AS varchar) AS credit_id, CAST(m.id AS varchar) AS movie_id, m.title,"
            + " m.release_year, m.runtime_minutes, m.rating, mg.genre, c.kind, c.character,"
            + " c.department, c.job"
            + " FROM credits c"
            + " JOIN movies m ON m.id = c.movie_id"
            + " LEFT JOIN movie_genres mg ON mg.movie_id = m.id"
            + " WHERE CAST(c.id AS varchar) IN :creditIds";

    Query query = entityManager.createNativeQuery(sql);
    query.setParameter("creditIds", orderedCreditIds.stream().map(UUID::toString).toList());

    List<Object[]> rows = query.getResultList();
    Map<UUID, EntryBuilder> byCreditId = new LinkedHashMap<>();
    for (Object[] row : rows) {
      UUID creditId = UUID.fromString((String) row[0]);
      EntryBuilder builder = byCreditId.computeIfAbsent(creditId, id -> new EntryBuilder());
      builder.movieId = UUID.fromString((String) row[1]);
      builder.title = (String) row[2];
      builder.releaseYear = ((Number) row[3]).intValue();
      builder.runtimeMinutes = row[4] == null ? null : ((Number) row[4]).intValue();
      builder.rating = row[5] == null ? null : toBigDecimal(row[5]);
      if (row[6] != null) {
        builder.genres.add(Genre.valueOf((String) row[6]));
      }
      builder.kind = (String) row[7];
      builder.character = (String) row[8];
      builder.department = (String) row[9];
      builder.job = (String) row[10];
    }

    return orderedCreditIds.stream()
        .map(byCreditId::get)
        .filter(java.util.Objects::nonNull)
        .map(EntryBuilder::toEntry)
        .toList();
  }

  private static BigDecimal toBigDecimal(Object value) {
    if (value instanceof BigDecimal bigDecimal) {
      return bigDecimal;
    }
    return new BigDecimal(value.toString());
  }

  /** Accumulates the rows of one credit (potentially several, one per genre) into one entry. */
  private static final class EntryBuilder {
    private UUID movieId;
    private String title;
    private int releaseYear;
    private Integer runtimeMinutes;
    private BigDecimal rating;
    private final Set<Genre> genres = new LinkedHashSet<>();
    private String kind;
    private String character;
    private String department;
    private String job;

    FilmographyEntry toEntry() {
      FilmographyMovieSummary movie =
          FilmographyMovieSummary.of(
              movieId,
              title,
              releaseYear,
              List.copyOf(genres),
              runtimeMinutes,
              rating == null ? null : new Rating(rating));
      Capacity capacity =
          "CAST".equals(kind)
              ? Capacity.Acting.of(character)
              : new Capacity.NonActing(department, job);
      return new FilmographyEntry(movie, capacity);
    }
  }
}
