package com.acme.catalog.movies.adapters.in.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.acme.catalog.movies.application.port.in.GetMovieUseCase;
import com.acme.catalog.movies.application.port.in.SearchMoviesQuery;
import com.acme.catalog.movies.application.port.in.SearchMoviesUseCase;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.generated.api.MoviesApi;
import com.acme.generated.model.Link;
import com.acme.generated.model.MovieDetail;
import com.acme.generated.model.MovieEnvelope;
import com.acme.generated.model.MovieLinks;
import com.acme.generated.model.MovieSearchEmbedded;
import com.acme.generated.model.MovieSearchEnvelope;
import com.acme.generated.model.MovieSearchPage;
import com.acme.generated.model.MovieSummary;
import com.acme.generated.model.Pagination;
import com.acme.platform.web.PageLinkBuilder;
import com.acme.platform.web.ResponseMetaFactory;
import com.acme.shared.domain.ResultPage;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

/**
 * Retrieves one movie's curated details ({@code GET /movies/{id}}, UC-001) and searches the catalog
 * a page at a time ({@code GET /movies}, UC-002). Content-Type is always {@code application/json},
 * never negotiated to {@code application/problem+json}. Class-annotated {@code @Validated}, so the
 * generated parameter constraints are enforced before a handler runs (design D3).
 */
@RestController
@Validated
public class MovieController implements MoviesApi {

  private final GetMovieUseCase getMovieUseCase;
  private final SearchMoviesUseCase searchMoviesUseCase;
  private final ResponseMetaFactory responseMetaFactory;
  private final MovieSearchLinks movieSearchLinks;

  public MovieController(
      GetMovieUseCase getMovieUseCase,
      SearchMoviesUseCase searchMoviesUseCase,
      ResponseMetaFactory responseMetaFactory,
      PageLinkBuilder pageLinkBuilder) {
    this.getMovieUseCase = getMovieUseCase;
    this.searchMoviesUseCase = searchMoviesUseCase;
    this.responseMetaFactory = responseMetaFactory;
    this.movieSearchLinks = new MovieSearchLinks(pageLinkBuilder);
  }

  @Override
  public ResponseEntity<MovieEnvelope> getMovie(UUID id) {
    Movie movie = getMovieUseCase.getMovie(new MovieId(id));

    MovieDetail data =
        new MovieDetail(
                movie.id().value(),
                movie.title(),
                movie.releaseYear(),
                movie.genres(),
                selfLinks(movie))
            .runtimeMinutes(movie.runtime().map(rm -> rm.value()).orElse(null))
            .synopsis(movie.synopsis().orElse(null))
            .rating(ratingLiteral(movie));

    MovieEnvelope envelope = new MovieEnvelope(data, responseMetaFactory.create());
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(envelope);
  }

  @Override
  public ResponseEntity<MovieSearchEnvelope> searchMovies(
      String title,
      List<String> genre,
      Integer releaseYearFrom,
      Integer releaseYearTo,
      BigDecimal minRating,
      String sort,
      Integer page,
      Integer size) {
    ResultPage<Movie> result =
        searchMoviesUseCase.search(
            new SearchMoviesQuery(
                title, genre, releaseYearFrom, releaseYearTo, minRating, sort, page, size));

    List<MovieSummary> summaries = result.items().stream().map(this::toSummary).toList();
    MovieSearchPage data =
        new MovieSearchPage(new MovieSearchEmbedded(summaries), movieSearchLinks.linksFor(result));
    Pagination pagination =
        new Pagination(result.page(), result.size(), result.totalElements(), result.totalPages());

    MovieSearchEnvelope envelope =
        new MovieSearchEnvelope(data, responseMetaFactory.create(pagination));
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(envelope);
  }

  /** A summary carries no synopsis (UC-002). */
  private MovieSummary toSummary(Movie movie) {
    return new MovieSummary(
            movie.id().value(),
            movie.title(),
            movie.releaseYear(),
            movie.genres(),
            selfLinks(movie))
        .runtimeMinutes(movie.runtime().map(rm -> rm.value()).orElse(null))
        .rating(ratingLiteral(movie));
  }

  private static MovieLinks selfLinks(Movie movie) {
    URI self = linkTo(methodOn(MoviesApi.class).getMovie(movie.id().value())).toUri();
    return new MovieLinks(new Link(self));
  }

  private static BigDecimal ratingLiteral(Movie movie) {
    return movie.rating().map(r -> r.value().stripTrailingZeros()).orElse(null);
  }
}
