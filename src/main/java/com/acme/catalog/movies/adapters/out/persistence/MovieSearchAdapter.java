package com.acme.catalog.movies.adapters.out.persistence;

import com.acme.catalog.movies.application.port.out.GenreVocabularyPort;
import com.acme.catalog.movies.application.port.out.SearchMoviesPort;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.catalog.movies.domain.model.MovieOrder;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSummary;
import com.acme.catalog.movies.domain.model.PageRequest;
import com.acme.catalog.movies.domain.model.Rating;
import com.acme.catalog.movies.domain.model.ResultPage;
import com.acme.catalog.movies.domain.model.RuntimeMinutes;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbound adapter searching the curated catalog (UC-002, design D5). A search runs a count, then
 * one ordered page of ids with {@code LIMIT/OFFSET} in SQL, then those movies with their genres in
 * one query — so paging never happens in memory and there is no N+1.
 */
@Component
public class MovieSearchAdapter implements SearchMoviesPort, GenreVocabularyPort {

  private static final char LIKE_ESCAPE = '\\';

  private final EntityManager entityManager;
  private final MovieJpaRepository movieJpaRepository;

  public MovieSearchAdapter(EntityManager entityManager, MovieJpaRepository movieJpaRepository) {
    this.entityManager = entityManager;
    this.movieJpaRepository = movieJpaRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public ResultPage<MovieSummary> search(
      MovieSearchCriteria criteria, MovieOrder order, PageRequest pageRequest) {
    long total = count(criteria);
    if (pageRequest.offset() >= total) {
      return ResultPage.of(List.of(), pageRequest, total);
    }
    List<UUID> ids = pageOfIds(criteria, order, pageRequest);
    Map<UUID, MovieJpaEntity> byId =
        movieJpaRepository.findAllByIdIn(ids).stream()
            .collect(Collectors.toMap(MovieJpaEntity::getId, Function.identity()));
    List<MovieSummary> items = ids.stream().map(byId::get).map(this::toSummary).toList();
    return ResultPage.of(items, pageRequest, total);
  }

  @Override
  @Transactional(readOnly = true)
  public Set<String> unknownGenres(Set<String> lowerCasedNames) {
    if (lowerCasedNames.isEmpty()) {
      return Set.of();
    }
    List<String> known =
        entityManager
            .createQuery(
                "select lower(g.name) from GenreJpaEntity g where lower(g.name) in :names",
                String.class)
            .setParameter("names", lowerCasedNames)
            .getResultList();
    Set<String> unknown = new HashSet<>(lowerCasedNames);
    unknown.removeAll(known);
    return unknown;
  }

  private long count(MovieSearchCriteria criteria) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Long> query = cb.createQuery(Long.class);
    Root<MovieJpaEntity> movie = query.from(MovieJpaEntity.class);
    query.select(cb.count(movie)).where(predicates(cb, query, movie, criteria));
    return entityManager.createQuery(query).getSingleResult();
  }

  private List<UUID> pageOfIds(
      MovieSearchCriteria criteria, MovieOrder order, PageRequest pageRequest) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<UUID> query = cb.createQuery(UUID.class);
    Root<MovieJpaEntity> movie = query.from(MovieJpaEntity.class);
    query
        .select(movie.get("id"))
        .where(predicates(cb, query, movie, criteria))
        .orderBy(orderBy(cb, movie, order));
    // offset < total here, and a curated catalog is far smaller than Integer.MAX_VALUE.
    return entityManager
        .createQuery(query)
        .setFirstResult(Math.toIntExact(pageRequest.offset()))
        .setMaxResults(pageRequest.size())
        .getResultList();
  }

  private Predicate[] predicates(
      CriteriaBuilder cb,
      AbstractQuery<?> query,
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
                        "%" + escapeLike(term.toLowerCase(Locale.ROOT)) + "%",
                        LIKE_ESCAPE)));
    // One EXISTS per genre: a movie must carry every given genre (BR-4).
    for (String genre : criteria.genres()) {
      Subquery<Integer> carriesGenre = query.subquery(Integer.class);
      Root<MovieJpaEntity> sameMovie = carriesGenre.correlate(movie);
      Join<MovieJpaEntity, GenreJpaEntity> genres = sameMovie.join("genres");
      carriesGenre.select(cb.literal(1)).where(cb.equal(cb.lower(genres.get("name")), genre));
      predicates.add(cb.exists(carriesGenre));
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
            min -> {
              Expression<BigDecimal> rating = movie.get("rating");
              // Unrated movies have not shown they meet the minimum (BR-4).
              predicates.add(cb.isNotNull(rating));
              predicates.add(cb.greaterThanOrEqualTo(rating, min.value()));
            });
    return predicates.toArray(Predicate[]::new);
  }

  /** The design D5 order table: the chosen key, then title A–Z, then id as the final tiebreak. */
  private static List<Order> orderBy(
      CriteriaBuilder cb, Root<MovieJpaEntity> movie, MovieOrder order) {
    Expression<String> title = cb.lower(movie.get("title"));
    Expression<?> releaseYear = movie.get("releaseYear");
    Expression<?> rating = movie.get("rating");
    Expression<Integer> unratedLast =
        cb.<Integer>selectCase().when(cb.isNull(rating), 1).otherwise(0);
    List<Order> orders =
        switch (order) {
          case TITLE_ASC -> List.of(cb.asc(title));
          case TITLE_DESC -> List.of(cb.desc(title));
          case RELEASE_YEAR_ASC -> List.of(cb.asc(releaseYear), cb.asc(title));
          case RELEASE_YEAR_DESC -> List.of(cb.desc(releaseYear), cb.asc(title));
          case RATING_ASC -> List.of(cb.asc(unratedLast), cb.asc(rating), cb.asc(title));
          case RATING_DESC -> List.of(cb.asc(unratedLast), cb.desc(rating), cb.asc(title));
        };
    List<Order> complete = new ArrayList<>(orders);
    complete.add(cb.asc(movie.get("id")));
    return complete;
  }

  /** Makes {@code %}, {@code _} and the escape character itself match literally. */
  static String escapeLike(String term) {
    StringBuilder escaped = new StringBuilder(term.length());
    for (char c : term.toCharArray()) {
      if (c == LIKE_ESCAPE || c == '%' || c == '_') {
        escaped.append(LIKE_ESCAPE);
      }
      escaped.append(c);
    }
    return escaped.toString();
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
