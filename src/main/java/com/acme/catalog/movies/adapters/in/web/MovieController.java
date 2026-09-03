package com.acme.catalog.movies.adapters.in.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.acme.catalog.movies.application.port.in.GetMovieByIdUseCase;
import com.acme.catalog.movies.application.port.in.SearchMoviesUseCase;
import com.acme.catalog.movies.domain.model.Genre;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.catalog.movies.domain.model.MoviePage;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSort;
import com.acme.catalog.movies.domain.model.MovieSortField;
import com.acme.catalog.movies.domain.model.Rating;
import com.acme.catalog.movies.domain.model.SortDirection;
import com.acme.common.web.CorrelationId;
import com.acme.generated.api.MoviesApi;
import com.acme.generated.model.Link;
import com.acme.generated.model.Meta;
import com.acme.generated.model.MovieCollectionData;
import com.acme.generated.model.MovieCollectionDataEmbedded;
import com.acme.generated.model.MovieCollectionEnvelope;
import com.acme.generated.model.MovieCollectionLinks;
import com.acme.generated.model.MovieDetail;
import com.acme.generated.model.MovieDetailEnvelope;
import com.acme.generated.model.MovieLinks;
import com.acme.generated.model.MovieSummary;
import com.acme.generated.model.MovieSummaryLinks;
import com.acme.generated.model.Pagination;
import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implements the generated {@link MoviesApi}. Thin: calls {@link GetMovieByIdUseCase}/{@link
 * SearchMoviesUseCase}, maps domain types to the generated DTOs, and assembles HAL links via {@link
 * org.springframework.hateoas.server.mvc.WebMvcLinkBuilder}. No business logic lives here; no
 * HATEOAS import in domain/application. {@code @Validated} enforces the generated
 * {@code @Min}/{@code @Max} query-param constraints, since the interface-level {@code @Validated}
 * on {@link MoviesApi} is not, by itself, honoured on the implementing bean (same rationale as
 * {@code SampleController}).
 */
@RestController
@Validated
public class MovieController implements MoviesApi {

  private final GetMovieByIdUseCase getMovieByIdUseCase;
  private final SearchMoviesUseCase searchMoviesUseCase;

  public MovieController(
      GetMovieByIdUseCase getMovieByIdUseCase, SearchMoviesUseCase searchMoviesUseCase) {
    this.getMovieByIdUseCase = getMovieByIdUseCase;
    this.searchMoviesUseCase = searchMoviesUseCase;
  }

  @Override
  public ResponseEntity<MovieDetailEnvelope> getMovieById(UUID id, UUID xCorrelationId) {
    Movie movie = getMovieByIdUseCase.getMovieById(new MovieId(id));
    String correlationId = CorrelationId.current();

    URI selfHref =
        URI.create(
            linkTo(methodOn(MoviesApi.class).getMovieById(id, xCorrelationId))
                .withSelfRel()
                .getHref());

    MovieDetail data =
        new MovieDetail(
                movie.id().value(),
                movie.title(),
                movie.releaseYear(),
                movie.genres().stream().map(genre -> genre.label()).toList(),
                new MovieLinks(new Link(selfHref)))
            .runtimeMinutes(movie.runtimeMinutes().orElse(null))
            .synopsis(movie.synopsis().orElse(null))
            .rating(movie.rating().map(rating -> rating.score()).orElse(null));

    Meta meta = new Meta(OffsetDateTime.now(ZoneOffset.UTC), UUID.fromString(correlationId));

    return ResponseEntity.ok(new MovieDetailEnvelope(data, meta));
  }

  @Override
  public ResponseEntity<MovieCollectionEnvelope> listMovies(
      UUID xCorrelationId,
      Integer page,
      Integer size,
      String title,
      List<String> genre,
      Integer yearFrom,
      Integer yearTo,
      BigDecimal minRating,
      String sort) {
    MovieSearchCriteria criteria = toCriteria(title, genre, yearFrom, yearTo, minRating);
    MovieSort movieSort = parseSort(sort);

    MoviePage moviePage = searchMoviesUseCase.searchMovies(criteria, page, size, movieSort);
    String correlationId = CorrelationId.current();

    List<MovieSummary> items = moviePage.items().stream().map(MovieController::toSummary).toList();

    MovieCollectionData data =
        new MovieCollectionData(
            new MovieCollectionDataEmbedded(items),
            collectionLinks(moviePage, title, genre, yearFrom, yearTo, minRating, sort));

    Meta meta =
        new Meta(OffsetDateTime.now(ZoneOffset.UTC), UUID.fromString(correlationId))
            .pagination(
                new Pagination(
                    moviePage.page(),
                    moviePage.size(),
                    moviePage.totalElements(),
                    moviePage.totalPages()));

    return ResponseEntity.ok(new MovieCollectionEnvelope(data, meta));
  }

  private static MovieSearchCriteria toCriteria(
      String title, List<String> genre, Integer yearFrom, Integer yearTo, BigDecimal minRating) {
    List<Genre> genres = genre == null ? List.of() : genre.stream().map(Genre::new).toList();
    return new MovieSearchCriteria(
        Optional.ofNullable(title),
        genres,
        Optional.ofNullable(yearFrom),
        Optional.ofNullable(yearTo),
        Optional.ofNullable(minRating).map(Rating::new));
  }

  /**
   * Parses {@code sort=<field>,<dir>}. An unknown field or direction throws {@link
   * IllegalArgumentException}, which the global {@code @RestControllerAdvice}'s {@code
   * onBadRequest} maps to {@code 400 problem+json} — deliberately not {@link
   * com.acme.common.error.ValidationException}, which maps to {@code 422} (see {@code
   * openspec/changes/add-movie-search/design.md}, D5).
   */
  private static MovieSort parseSort(String sort) {
    if (sort == null || sort.isBlank()) {
      return MovieSort.DEFAULT;
    }
    String[] parts = sort.split(",", 2);
    if (parts.length != 2) {
      throw new IllegalArgumentException("Invalid sort parameter: " + sort);
    }
    MovieSortField field = parseSortField(parts[0]);
    SortDirection direction = parseSortDirection(parts[1]);
    return new MovieSort(field, direction);
  }

  private static MovieSortField parseSortField(String field) {
    return switch (field.trim().toLowerCase(Locale.ROOT)) {
      case "title" -> MovieSortField.TITLE;
      case "releaseyear" -> MovieSortField.RELEASE_YEAR;
      case "rating" -> MovieSortField.RATING;
      default -> throw new IllegalArgumentException("Unsupported sort field: " + field);
    };
  }

  private static SortDirection parseSortDirection(String direction) {
    return switch (direction.trim().toLowerCase(Locale.ROOT)) {
      case "asc" -> SortDirection.ASC;
      case "desc" -> SortDirection.DESC;
      default -> throw new IllegalArgumentException("Unsupported sort direction: " + direction);
    };
  }

  private static MovieSummary toSummary(Movie movie) {
    return new MovieSummary(
            movie.id().value(),
            movie.title(),
            movie.releaseYear(),
            movie.genres().stream().map(Genre::label).toList(),
            new MovieSummaryLinks(itemSelfLink(movie.id().value())))
        .runtimeMinutes(movie.runtimeMinutes().orElse(null))
        .rating(movie.rating().map(Rating::score).orElse(null));
  }

  private static Link itemSelfLink(UUID id) {
    URI href =
        URI.create(
            linkTo(methodOn(MoviesApi.class).getMovieById(id, null)).withSelfRel().getHref());
    return new Link(href);
  }

  private static MovieCollectionLinks collectionLinks(
      MoviePage moviePage,
      String title,
      List<String> genre,
      Integer yearFrom,
      Integer yearTo,
      BigDecimal minRating,
      String sort) {
    int page = moviePage.page();
    int size = moviePage.size();
    int totalPages = moviePage.totalPages();

    MovieCollectionLinks links =
        new MovieCollectionLinks(
            pageLink(page, size, title, genre, yearFrom, yearTo, minRating, sort));
    links.first(pageLink(0, size, title, genre, yearFrom, yearTo, minRating, sort));
    if (totalPages > 0) {
      links.last(pageLink(totalPages - 1, size, title, genre, yearFrom, yearTo, minRating, sort));
    } else {
      links.last(pageLink(0, size, title, genre, yearFrom, yearTo, minRating, sort));
    }
    if (page > 0) {
      links.prev(pageLink(page - 1, size, title, genre, yearFrom, yearTo, minRating, sort));
    }
    if (page < totalPages - 1) {
      links.next(pageLink(page + 1, size, title, genre, yearFrom, yearTo, minRating, sort));
    }
    return links;
  }

  private static Link pageLink(
      int page,
      int size,
      String title,
      List<String> genre,
      Integer yearFrom,
      Integer yearTo,
      BigDecimal minRating,
      String sort) {
    return new Link(
        linkTo(
                methodOn(MoviesApi.class)
                    .listMovies(null, page, size, title, genre, yearFrom, yearTo, minRating, sort))
            .toUri());
  }
}
