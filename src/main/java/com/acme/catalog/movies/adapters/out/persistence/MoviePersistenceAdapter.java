package com.acme.catalog.movies.adapters.out.persistence;

import com.acme.catalog.movies.application.port.out.LoadGenreVocabularyPort;
import com.acme.catalog.movies.application.port.out.LoadMoviePort;
import com.acme.catalog.movies.application.port.out.SearchMoviesPort;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSortOrder;
import com.acme.catalog.movies.domain.model.MovieSummary;
import com.acme.catalog.movies.domain.model.Rating;
import com.acme.catalog.movies.domain.model.RuntimeMinutes;
import com.acme.shared.domain.paging.Page;
import com.acme.shared.domain.paging.PageRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbound adapter loading and searching curated movies in PostgreSQL, mapping {@link
 * MovieJpaEntity} to the domain (design D5). Runs the movie search as three queries: a count, a
 * page of ids (never joining genres, so its {@code ORDER BY} of non-selected expressions is
 * portable and no row is duplicated), then the entities for those ids with genres fetched, re-
 * ordered in memory to the ids' order.
 */
@Component
public class MoviePersistenceAdapter
    implements LoadMoviePort, LoadGenreVocabularyPort, SearchMoviesPort {

  private static final char ESCAPE_CHAR = '\\';
  private static final String TITLE_PATTERN_PARAMETER = "titlePattern";

  private final MovieJpaRepository movieJpaRepository;
  private final GenreJpaRepository genreJpaRepository;
  private final EntityManager entityManager;

  public MoviePersistenceAdapter(
      MovieJpaRepository movieJpaRepository,
      GenreJpaRepository genreJpaRepository,
      EntityManager entityManager) {
    this.movieJpaRepository = movieJpaRepository;
    this.genreJpaRepository = genreJpaRepository;
    this.entityManager = entityManager;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Movie> loadMovie(MovieId id) {
    return movieJpaRepository.findById(id.value()).map(this::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Set<String> loadGenreNames() {
    return genreJpaRepository.findAll().stream()
        .map(GenreJpaEntity::getName)
        .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  @Transactional(readOnly = true)
  public Page<MovieSummary> search(
      MovieSearchCriteria criteria, MovieSortOrder order, PageRequest pageRequest) {
    long totalElements = count(criteria);

    if (pageRequest.offset() >= totalElements) {
      return new Page<>(List.of(), pageRequest, totalElements);
    }

    List<UUID> ids = pageOfIds(criteria, order, pageRequest);
    List<MovieSummary> summaries = summariesInOrder(ids);

    return new Page<>(summaries, pageRequest, totalElements);
  }

  private long count(MovieSearchCriteria criteria) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Long> query = cb.createQuery(Long.class);
    Root<MovieJpaEntity> root = query.from(MovieJpaEntity.class);
    query.select(cb.count(root));
    query.where(predicates(cb, query, root, criteria));
    return bindTitlePattern(entityManager.createQuery(query), criteria).getSingleResult();
  }

  private List<UUID> pageOfIds(
      MovieSearchCriteria criteria, MovieSortOrder order, PageRequest pageRequest) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<UUID> query = cb.createQuery(UUID.class);
    Root<MovieJpaEntity> root = query.from(MovieJpaEntity.class);
    query.select(root.get("id"));
    query.where(predicates(cb, query, root, criteria));
    query.orderBy(orderBy(cb, root, order));

    return bindTitlePattern(entityManager.createQuery(query), criteria)
        .setFirstResult(Math.toIntExact(pageRequest.offset()))
        .setMaxResults(pageRequest.size())
        .getResultList();
  }

  private List<MovieSummary> summariesInOrder(List<UUID> ids) {
    List<MovieJpaEntity> entities = movieJpaRepository.findAllById(ids);
    Map<UUID, MovieJpaEntity> byId = new HashMap<>();
    entities.forEach(entity -> byId.put(entity.getId(), entity));

    // A movie removed between the ids query and this one is skipped, not a 500 (design Risks).
    return ids.stream().map(byId::get).filter(Objects::nonNull).map(this::toSummary).toList();
  }

  /**
   * Binds the title LIKE pattern as a named parameter when a title term is present, so the term
   * never becomes part of the SQL text (design D1). Must be applied to every query built with
   * {@link #predicates}.
   */
  private static <T> TypedQuery<T> bindTitlePattern(
      TypedQuery<T> query, MovieSearchCriteria criteria) {
    criteria
        .titleTerm()
        .ifPresent(
            term -> query.setParameter(TITLE_PATTERN_PARAMETER, "%" + escapeLikeTerm(term) + "%"));
    return query;
  }

  private Predicate[] predicates(
      CriteriaBuilder cb,
      CriteriaQuery<?> outerQuery,
      Root<MovieJpaEntity> root,
      MovieSearchCriteria criteria) {
    List<Predicate> predicates = new ArrayList<>();

    criteria
        .titleTerm()
        .ifPresent(
            term ->
                predicates.add(
                    cb.like(
                        cb.lower(root.get("title")),
                        cb.lower(cb.parameter(String.class, TITLE_PATTERN_PARAMETER)),
                        ESCAPE_CHAR)));

    for (String genreName : criteria.genres()) {
      predicates.add(genreExists(cb, outerQuery, root, genreName));
    }

    criteria
        .releaseYearFrom()
        .ifPresent(from -> predicates.add(cb.greaterThanOrEqualTo(root.get("releaseYear"), from)));
    criteria
        .releaseYearTo()
        .ifPresent(to -> predicates.add(cb.lessThanOrEqualTo(root.get("releaseYear"), to)));

    criteria
        .minRating()
        .ifPresent(
            minRating -> {
              predicates.add(cb.isNotNull(root.get("rating")));
              predicates.add(cb.greaterThanOrEqualTo(root.get("rating"), minRating.value()));
            });

    return predicates.toArray(Predicate[]::new);
  }

  private Predicate genreExists(
      CriteriaBuilder cb,
      CriteriaQuery<?> outerQuery,
      Root<MovieJpaEntity> root,
      String genreName) {
    Subquery<UUID> subquery = outerQuery.subquery(UUID.class);
    Root<MovieJpaEntity> correlatedRoot = subquery.correlate(root);
    Join<MovieJpaEntity, GenreJpaEntity> genreJoin = correlatedRoot.join("genres");
    subquery.select(correlatedRoot.get("id"));
    subquery.where(cb.equal(cb.lower(genreJoin.get("name")), genreName.toLowerCase(Locale.ROOT)));
    return cb.exists(subquery);
  }

  private List<Order> orderBy(CriteriaBuilder cb, Root<MovieJpaEntity> root, MovieSortOrder order) {
    List<Order> orders = new ArrayList<>();
    switch (order) {
      case TITLE_ASC -> orders.add(cb.asc(cb.lower(root.get("title"))));
      case TITLE_DESC -> orders.add(cb.desc(cb.lower(root.get("title"))));
      case RELEASE_YEAR_ASC -> orders.add(cb.asc(root.get("releaseYear")));
      case RELEASE_YEAR_DESC -> orders.add(cb.desc(root.get("releaseYear")));
      case RATING_ASC -> {
        orders.add(cb.asc(unratedLastKey(cb, root)));
        orders.add(cb.asc(root.get("rating")));
      }
      case RATING_DESC -> {
        orders.add(cb.asc(unratedLastKey(cb, root)));
        orders.add(cb.desc(root.get("rating")));
      }
      case DEFAULT -> {
        orders.add(cb.desc(root.get("releaseYear")));
        orders.add(cb.asc(cb.lower(root.get("title"))));
      }
    }
    orders.add(cb.asc(root.get("id")));
    return orders;
  }

  private Expression<Integer> unratedLastKey(CriteriaBuilder cb, Root<MovieJpaEntity> root) {
    return cb.<Integer>selectCase().when(cb.isNull(root.get("rating")), 1).otherwise(0);
  }

  private static String escapeLikeTerm(String term) {
    StringBuilder escaped = new StringBuilder();
    for (int i = 0; i < term.length(); i++) {
      char c = term.charAt(i);
      if (c == ESCAPE_CHAR || c == '%' || c == '_') {
        escaped.append(ESCAPE_CHAR);
      }
      escaped.append(c);
    }
    return escaped.toString();
  }

  private Movie toDomain(MovieJpaEntity entity) {
    return new Movie(
        new MovieId(entity.getId()),
        entity.getTitle(),
        entity.getReleaseYear(),
        entity.getGenres().stream().map(GenreJpaEntity::getName).toList(),
        Optional.ofNullable(entity.getRuntimeMinutes()).map(RuntimeMinutes::new),
        Optional.ofNullable(entity.getSynopsis()).filter(s -> !s.isBlank()),
        Optional.ofNullable(entity.getRating()).map(Rating::new));
  }

  private MovieSummary toSummary(MovieJpaEntity entity) {
    return new MovieSummary(
        new MovieId(entity.getId()),
        entity.getTitle(),
        entity.getReleaseYear(),
        entity.getGenres().stream().map(GenreJpaEntity::getName).toList(),
        Optional.ofNullable(entity.getRuntimeMinutes()).map(RuntimeMinutes::new),
        Optional.ofNullable(entity.getRating()).map(Rating::new));
  }
}
