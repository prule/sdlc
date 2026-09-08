package com.acme.catalog.movies.adapters.in.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.acme.catalog.movies.application.port.in.GetMovieDetailUseCase;
import com.acme.catalog.movies.application.port.in.SearchMoviesUseCase;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MoviePage;
import com.acme.catalog.movies.domain.model.MoviePageRequest;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSort;
import com.acme.common.web.CorrelationId;
import com.acme.generated.api.MoviesApi;
import com.acme.generated.model.Genre;
import com.acme.generated.model.Link;
import com.acme.generated.model.Meta;
import com.acme.generated.model.MovieCollectionLinks;
import com.acme.generated.model.MovieDetail;
import com.acme.generated.model.MovieDetailEnvelope;
import com.acme.generated.model.MovieLinks;
import com.acme.generated.model.MovieSummary;
import com.acme.generated.model.MovieSummaryCollectionData;
import com.acme.generated.model.MovieSummaryCollectionDataEmbedded;
import com.acme.generated.model.MovieSummaryCollectionEnvelope;
import com.acme.generated.model.Pagination;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implements the generated {@link MoviesApi}. Thin: calls the use case(s), maps the domain {@link
 * Movie} to the generated DTOs, and builds HAL links via {@link
 * org.springframework.hateoas.server.mvc.WebMvcLinkBuilder}. Link assembly is web-adapter-only —
 * the domain and application layers know nothing about hypermedia. {@code @Validated} enforces the
 * generated {@code @Min}/{@code @Max} query-param constraints (see {@code
 * com.acme.platform.sample.adapters.in.web.SampleController} for the same note).
 */
@RestController
@Validated
public class MovieController implements MoviesApi {

  private final GetMovieDetailUseCase getMovieDetailUseCase;
  private final SearchMoviesUseCase searchMoviesUseCase;

  public MovieController(
      GetMovieDetailUseCase getMovieDetailUseCase, SearchMoviesUseCase searchMoviesUseCase) {
    this.getMovieDetailUseCase = getMovieDetailUseCase;
    this.searchMoviesUseCase = searchMoviesUseCase;
  }

  @Override
  public ResponseEntity<MovieDetailEnvelope> getMovieById(UUID id, UUID xCorrelationId) {
    Movie movie = getMovieDetailUseCase.getMovieDetail(id);
    String correlationId = CorrelationId.current();

    MovieDetail data = toMovieDetail(movie);
    Meta meta = new Meta(OffsetDateTime.now(ZoneOffset.UTC), UUID.fromString(correlationId));

    return ResponseEntity.ok(new MovieDetailEnvelope(data, meta));
  }

  @Override
  public ResponseEntity<MovieSummaryCollectionEnvelope> getMovies(
      UUID xCorrelationId,
      Integer page,
      Integer size,
      String title,
      List<Genre> genre,
      Integer releaseYearFrom,
      Integer releaseYearTo,
      BigDecimal minRating,
      String sort) {
    MovieSearchCriteria criteria =
        MovieSearchCriteria.of(
            title, toDomainGenres(genre), releaseYearFrom, releaseYearTo, minRating);
    MoviePageRequest pageRequest = new MoviePageRequest(page, size);
    MovieSort movieSort = MovieSort.parse(sort);
    String sortForLink = movieSort.equals(MovieSort.defaultSort()) ? null : sort;

    MoviePage moviePage = searchMoviesUseCase.search(criteria, pageRequest, movieSort);
    String correlationId = CorrelationId.current();

    List<MovieSummary> summaries =
        moviePage.content().stream().map(MovieController::toSummary).toList();

    MovieSummaryCollectionData data =
        new MovieSummaryCollectionData(
            new MovieSummaryCollectionDataEmbedded(summaries),
            collectionLinks(
                moviePage, title, genre, releaseYearFrom, releaseYearTo, minRating, sortForLink));

    Meta meta =
        new Meta(OffsetDateTime.now(ZoneOffset.UTC), UUID.fromString(correlationId))
            .pagination(
                new Pagination(
                    moviePage.page(),
                    moviePage.size(),
                    moviePage.totalElements(),
                    moviePage.totalPages()));

    return ResponseEntity.ok(new MovieSummaryCollectionEnvelope(data, meta));
  }

  private static Set<com.acme.catalog.movies.domain.model.Genre> toDomainGenres(
      List<Genre> genres) {
    if (genres == null) {
      return Set.of();
    }
    return genres.stream()
        .map(genre -> com.acme.catalog.movies.domain.model.Genre.valueOf(genre.name()))
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private static MovieDetail toMovieDetail(Movie movie) {
    MovieDetail data =
        new MovieDetail(
            movie.id(),
            movie.title(),
            movie.releaseYear(),
            movie.genres().stream().map(genre -> Genre.valueOf(genre.name())).toList(),
            new MovieLinks(selfLink(movie.id())));

    movie.runtimeMinutes().ifPresent(data::setRuntimeMinutes);
    movie.synopsis().ifPresent(data::setSynopsis);
    movie.rating().ifPresent(rating -> data.setRating(rating.value()));

    return data;
  }

  private static MovieSummary toSummary(Movie movie) {
    MovieSummary summary =
        new MovieSummary(
            movie.id(),
            movie.title(),
            movie.releaseYear(),
            movie.genres().stream().map(genre -> Genre.valueOf(genre.name())).toList(),
            new MovieLinks(selfLink(movie.id())));

    movie.runtimeMinutes().ifPresent(summary::setRuntimeMinutes);
    movie.rating().ifPresent(rating -> summary.setRating(rating.value()));

    return summary;
  }

  private static Link selfLink(UUID id) {
    return new Link(linkTo(methodOn(MoviesApi.class).getMovieById(id, null)).toUri());
  }

  private static MovieCollectionLinks collectionLinks(
      MoviePage moviePage,
      String title,
      List<Genre> genre,
      Integer releaseYearFrom,
      Integer releaseYearTo,
      BigDecimal minRating,
      String sort) {
    int page = moviePage.page();
    int size = moviePage.size();
    int totalPages = moviePage.totalPages();

    MovieCollectionLinks links =
        new MovieCollectionLinks(
            pageLink(page, size, title, genre, releaseYearFrom, releaseYearTo, minRating, sort));
    links.first(pageLink(0, size, title, genre, releaseYearFrom, releaseYearTo, minRating, sort));
    links.last(
        pageLink(
            Math.max(totalPages - 1, 0),
            size,
            title,
            genre,
            releaseYearFrom,
            releaseYearTo,
            minRating,
            sort));
    if (page > 0) {
      links.prev(
          pageLink(page - 1, size, title, genre, releaseYearFrom, releaseYearTo, minRating, sort));
    }
    if (page < totalPages - 1) {
      links.next(
          pageLink(page + 1, size, title, genre, releaseYearFrom, releaseYearTo, minRating, sort));
    }
    return links;
  }

  private static Link pageLink(
      int page,
      int size,
      String title,
      List<Genre> genre,
      Integer releaseYearFrom,
      Integer releaseYearTo,
      BigDecimal minRating,
      String sort) {
    return new Link(
        linkTo(
                methodOn(MoviesApi.class)
                    .getMovies(
                        null,
                        page,
                        size,
                        title,
                        genre,
                        releaseYearFrom,
                        releaseYearTo,
                        minRating,
                        sort))
            .toUri());
  }
}
