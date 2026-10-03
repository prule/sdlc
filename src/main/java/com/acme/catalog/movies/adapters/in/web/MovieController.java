package com.acme.catalog.movies.adapters.in.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.acme.catalog.movies.application.port.in.GetMovieUseCase;
import com.acme.catalog.movies.application.port.in.SearchMoviesQuery;
import com.acme.catalog.movies.application.port.in.SearchMoviesUseCase;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.catalog.movies.domain.model.ResultPage;
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
import com.acme.platform.web.ResponseMetaFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Retrieves one movie's curated details ({@code GET /movies/{id}}, UC-001) and searches the catalog
 * a page at a time ({@code GET /movies}, UC-002). Content-Type is always {@code application/json},
 * never negotiated to {@code application/problem+json} (design D1/D3).
 */
@RestController
public class MovieController implements MoviesApi {

  /** {@code searchMovies}'s query parameters, as the interface description names them. */
  static final List<String> SEARCH_PARAMETERS =
      List.of(
          "title",
          "genre",
          "releaseYearFrom",
          "releaseYearTo",
          "minRating",
          "sort",
          "page",
          "size");

  private final GetMovieUseCase getMovieUseCase;
  private final SearchMoviesUseCase searchMoviesUseCase;
  private final ResponseMetaFactory responseMetaFactory;
  private final HttpServletRequest request;
  private final PagedLinks pagedLinks = new PagedLinks(SEARCH_PARAMETERS);

  public MovieController(
      GetMovieUseCase getMovieUseCase,
      SearchMoviesUseCase searchMoviesUseCase,
      ResponseMetaFactory responseMetaFactory,
      HttpServletRequest request) {
    this.getMovieUseCase = getMovieUseCase;
    this.searchMoviesUseCase = searchMoviesUseCase;
    this.responseMetaFactory = responseMetaFactory;
    this.request = request;
  }

  @Override
  public ResponseEntity<MovieEnvelope> getMovie(UUID id) {
    Movie movie = getMovieUseCase.getMovie(new MovieId(id));

    MovieLinks links = new MovieLinks(new Link(detailsUri(movie.id().value())));

    MovieDetail data =
        new MovieDetail(
                movie.id().value(), movie.title(), movie.releaseYear(), movie.genres(), links)
            .runtimeMinutes(movie.runtime().map(rm -> rm.value()).orElse(null))
            .synopsis(movie.synopsis().orElse(null))
            .rating(movie.rating().map(r -> r.value().stripTrailingZeros()).orElse(null));

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
    ResultPage<com.acme.catalog.movies.domain.model.MovieSummary> result =
        searchMoviesUseCase.search(
            new SearchMoviesQuery(
                title,
                genresAsSent(genre),
                releaseYearFrom,
                releaseYearTo,
                minRating,
                sort,
                page,
                size));

    MovieSearchPage data =
        new MovieSearchPage(
            new MovieSearchEmbedded(result.items().stream().map(this::toSummary).toList()),
            pagedLinks.linksFor(request, result));
    Pagination pagination =
        new Pagination(result.page(), result.size(), result.totalElements(), result.totalPages());

    MovieSearchEnvelope envelope =
        new MovieSearchEnvelope(data, responseMetaFactory.create(pagination));
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(envelope);
  }

  /**
   * The {@code genre} values exactly as sent. Binding turns {@code genre=} into an empty list,
   * which would silently browse; an empty genre value must instead be refused (BR-10), so the use
   * case gets the raw values.
   */
  private List<String> genresAsSent(List<String> bound) {
    String[] raw = request.getParameterValues("genre");
    return raw == null ? bound : List.of(raw);
  }

  private MovieSummary toSummary(com.acme.catalog.movies.domain.model.MovieSummary movie) {
    return new MovieSummary(
            movie.id().value(),
            movie.title(),
            movie.releaseYear(),
            movie.genres(),
            new MovieLinks(new Link(detailsUri(movie.id().value()))))
        .runtimeMinutes(movie.runtime().map(rm -> rm.value()).orElse(null))
        .rating(movie.rating().map(r -> r.value().stripTrailingZeros()).orElse(null));
  }

  private static URI detailsUri(UUID id) {
    return linkTo(methodOn(MoviesApi.class).getMovie(id)).toUri();
  }
}
