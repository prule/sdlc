package com.acme.catalog.movies.adapters.out.persistence;

import com.acme.catalog.movies.application.port.out.LoadGenreVocabularyPort;
import com.acme.catalog.movies.application.port.out.SearchMoviesPort;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSortOrder;
import com.acme.shared.domain.PageSpec;
import com.acme.shared.domain.ResultPage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbound adapter searching the curated catalog with the JPA Criteria API (design D5). Values are
 * always bound, never concatenated into a query. One search runs at most three queries: a count; if
 * the page is not after the last, the page of ids in the requested order; then the movies for those
 * ids with their genres, re-ordered to match. Splitting the id page from hydration avoids in-memory
 * pagination over a collection fetch.
 */
@Component
public class MovieSearchAdapter implements SearchMoviesPort, LoadGenreVocabularyPort {

  private static final char LIKE_ESCAPE = '\\';

  private final EntityManager entityManager;
  private final MovieJpaRepository movieJpaRepository;

  public MovieSearchAdapter(EntityManager entityManager, MovieJpaRepository movieJpaRepository) {
    this.entityManager = entityManager;
    this.movieJpaRepository = movieJpaRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public ResultPage<Movie> search(
      MovieSearchCriteria criteria, MovieSortOrder order, PageSpec page) {
    long total = count(criteria);
    if (page.offset() >= total) {
      return ResultPage.empty(page, total);
    }
    List<UUID> ids = idPage(criteria, order, page);
    Map<UUID, MovieJpaEntity> byId =
        movieJpaRepository.findAllByIdIn(ids).stream()
            .collect(Collectors.toMap(MovieJpaEntity::getId, Function.identity()));
    List<Movie> movies = new ArrayList<>(ids.size());
    for (UUID id : ids) {
      MovieJpaEntity entity = byId.get(id);
      if (entity != null) {
        movies.add(MovieJpaMapper.toDomain(entity));
      }
    }
    return new ResultPage<>(movies, page.page(), page.size(), total);
  }

  @Override
  @Transactional(readOnly = true)
  public List<String> loadGenreNames() {
    HibernateCriteriaBuilder cb = criteriaBuilder();
    CriteriaQuery<String> query = cb.createQuery(String.class);
    Root<GenreJpaEntity> genre = query.from(GenreJpaEntity.class);
    query.select(genre.get("name"));
    return entityManager.createQuery(query).getResultList();
  }

  private long count(MovieSearchCriteria criteria) {
    HibernateCriteriaBuilder cb = criteriaBuilder();
    CriteriaQuery<Long> query = cb.createQuery(Long.class);
    Root<MovieJpaEntity> movie = query.from(MovieJpaEntity.class);
    query.select(cb.count(movie)).where(predicates(cb, query, movie, criteria));
    return entityManager.createQuery(query).getSingleResult();
  }

  private List<UUID> idPage(MovieSearchCriteria criteria, MovieSortOrder order, PageSpec page) {
    HibernateCriteriaBuilder cb = criteriaBuilder();
    CriteriaQuery<UUID> query = cb.createQuery(UUID.class);
    Root<MovieJpaEntity> movie = query.from(MovieJpaEntity.class);
    query
        .select(movie.get("id"))
        .where(predicates(cb, query, movie, criteria))
        .orderBy(orders(cb, movie, order));
    return entityManager
        .createQuery(query)
        // Only reached when offset < total, which is at most a long-sized row count but in
        // practice fits an int; the guard in search() keeps a huge page from ever getting here.
        .setFirstResult(Math.toIntExact(page.offset()))
        .setMaxResults(page.size())
        .getResultList();
  }

  private Predicate[] predicates(
      HibernateCriteriaBuilder cb,
      CommonAbstractCriteria query,
      Root<MovieJpaEntity> movie,
      MovieSearchCriteria criteria) {
    List<Predicate> predicates = new ArrayList<>();
    criteria
        .titleTerm()
        .ifPresent(
            term ->
                predicates.add(
                    cb.like(
                        cb.lower(movie.get("title")),
                        // value(), not literal(): the client's term is a bound parameter, never
                        // rendered into the SQL text.
                        cb.lower(cb.value(containsPattern(term))),
                        LIKE_ESCAPE)));
    if (!criteria.genres().isEmpty()) {
      Subquery<UUID> carriesAll = query.subquery(UUID.class);
      Root<MovieJpaEntity> candidate = carriesAll.from(MovieJpaEntity.class);
      Join<MovieJpaEntity, GenreJpaEntity> genre = candidate.join("genres");
      carriesAll
          .select(candidate.get("id"))
          .where(genre.get("name").in(criteria.genres()))
          .groupBy(candidate.get("id"))
          .having(cb.equal(cb.countDistinct(genre.get("id")), (long) criteria.genres().size()));
      predicates.add(movie.get("id").in(carriesAll));
    }
    criteria
        .releaseYearFrom()
        .ifPresent(from -> predicates.add(cb.greaterThanOrEqualTo(movie.get("releaseYear"), from)));
    criteria
        .releaseYearTo()
        .ifPresent(to -> predicates.add(cb.lessThanOrEqualTo(movie.get("releaseYear"), to)));
    criteria
        .minRating()
        .ifPresent(
            min -> predicates.add(cb.greaterThanOrEqualTo(movie.<BigDecimal>get("rating"), min)));
    return predicates.toArray(Predicate[]::new);
  }

  /** The complete, stable order for each sort (design D5); the movie id is the final tiebreak. */
  private List<Order> orders(
      HibernateCriteriaBuilder cb, Root<MovieJpaEntity> movie, MovieSortOrder order) {
    Expression<String> title = cb.lower(movie.get("title"));
    Order byId = cb.asc(movie.get("id"));
    boolean desc = order.descending();
    return switch (order.field()) {
      case TITLE -> List.of(desc ? cb.desc(title) : cb.asc(title), byId);
      case RELEASE_YEAR ->
          List.of(
              desc ? cb.desc(movie.get("releaseYear")) : cb.asc(movie.get("releaseYear")),
              cb.asc(title),
              byId);
      case RATING ->
          List.of(
              // Second argument is nullsFirst: false puts unrated movies last in both directions.
              desc ? cb.desc(movie.get("rating"), false) : cb.asc(movie.get("rating"), false),
              cb.asc(title),
              byId);
    };
  }

  /** {@code %term%}, with the LIKE wildcards and the escape character itself matched literally. */
  private static String containsPattern(String term) {
    String escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped + "%";
  }

  private HibernateCriteriaBuilder criteriaBuilder() {
    return (HibernateCriteriaBuilder) entityManager.getCriteriaBuilder();
  }
}
