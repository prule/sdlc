package com.acme.catalog.movies.adapters.in.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.acme.catalog.movies.application.port.in.GetMovieUseCase;
import com.acme.catalog.movies.application.port.in.SearchMoviesUseCase;
import com.acme.catalog.movies.domain.model.InvalidSearchCriterionException;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSortOrder;
import com.acme.catalog.movies.domain.model.Rating;
import com.acme.generated.api.MoviesApi;
import com.acme.generated.model.CollectionLinks;
import com.acme.generated.model.Link;
import com.acme.generated.model.MovieCollection;
import com.acme.generated.model.MovieCollectionEmbedded;
import com.acme.generated.model.MovieCollectionEnvelope;
import com.acme.generated.model.MovieDetail;
import com.acme.generated.model.MovieEnvelope;
import com.acme.generated.model.MovieLinks;
import com.acme.generated.model.Pagination;
import com.acme.platform.web.CollectionLinksFactory;
import com.acme.platform.web.InvalidQueryParameterException;
import com.acme.platform.web.ResponseMetaFactory;
import com.acme.shared.domain.paging.Page;
import com.acme.shared.domain.paging.PageRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Retrieves one movie's curated details ({@code GET /movies/{id}}, UC-001) and searches/browses
 * movies ({@code GET /movies}, UC-002). Content-Type is always {@code application/json}, never
 * negotiated to {@code application/problem+json} (design D1/D3).
 */
@RestController
public class MovieController implements MoviesApi {

  /** The query parameters {@code CollectionLinksFactory} preserves on navigation links (D6). */
  private static final Set<String> RECOGNISED_SEARCH_PARAMS =
      Set.of(
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
  private final CollectionLinksFactory collectionLinksFactory;
  private final HttpServletRequest request;

  public MovieController(
      GetMovieUseCase getMovieUseCase,
      SearchMoviesUseCase searchMoviesUseCase,
      ResponseMetaFactory responseMetaFactory,
      CollectionLinksFactory collectionLinksFactory,
      HttpServletRequest request) {
    this.getMovieUseCase = getMovieUseCase;
    this.searchMoviesUseCase = searchMoviesUseCase;
    this.responseMetaFactory = responseMetaFactory;
    this.collectionLinksFactory = collectionLinksFactory;
    this.request = request;
  }

  @Override
  public ResponseEntity<MovieEnvelope> getMovie(UUID id) {
    Movie movie = getMovieUseCase.getMovie(new MovieId(id));

    URI self = linkTo(methodOn(MoviesApi.class).getMovie(movie.id().value())).toUri();
    MovieLinks links = new MovieLinks(new Link(self));

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
  public ResponseEntity<MovieCollectionEnvelope> searchMovies(
      String title,
      List<String> genre,
      Integer releaseYearFrom,
      Integer releaseYearTo,
      BigDecimal minRating,
      String sort,
      Integer page,
      Integer size) {
    Page<com.acme.catalog.movies.domain.model.MovieSummary> resultPage;
    try {
      MovieSearchCriteria criteria =
          MovieSearchCriteria.of(
              Optional.ofNullable(title),
              rawGenreValues(),
              Optional.ofNullable(releaseYearFrom),
              Optional.ofNullable(releaseYearTo),
              Optional.ofNullable(minRating).map(Rating::new));
      MovieSortOrder order = MovieSortOrder.parse(Optional.ofNullable(sort));
      PageRequest pageRequest = new PageRequest(page, size);

      resultPage = searchMoviesUseCase.search(criteria, order, pageRequest);
    } catch (InvalidSearchCriterionException ex) {
      throw new InvalidQueryParameterException(parameterNameFor(ex));
    }

    List<com.acme.generated.model.MovieSummary> summaries =
        resultPage.items().stream().map(this::toDto).toList();
    MovieCollectionEmbedded embedded = new MovieCollectionEmbedded(summaries);
    CollectionLinks links = collectionLinksFactory.create(resultPage, RECOGNISED_SEARCH_PARAMS);
    MovieCollection data = new MovieCollection(embedded, links);

    Pagination pagination =
        new Pagination(
            resultPage.request().page(),
            resultPage.request().size(),
            resultPage.totalElements(),
            resultPage.totalPages());
    MovieCollectionEnvelope envelope =
        new MovieCollectionEnvelope(data, responseMetaFactory.create(pagination));
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(envelope);
  }

  /**
   * Reads {@code genre} directly from the servlet request, never the generated {@code List<String>}
   * (design D2/D6): Spring's conversion would silently turn {@code genre=} into an empty list and
   * split a comma-containing value, both of which must instead reach the vocabulary check.
   */
  private Set<String> rawGenreValues() {
    String[] values = request.getParameterValues("genre");
    return values == null ? Set.of() : new LinkedHashSet<>(Arrays.asList(values));
  }

  private static String parameterNameFor(InvalidSearchCriterionException ex) {
    return switch (ex.criterion()) {
      case SORT -> "sort";
      case RELEASE_YEAR_RANGE -> "releaseYearFrom";
      case GENRE -> "genre";
    };
  }

  private com.acme.generated.model.MovieSummary toDto(
      com.acme.catalog.movies.domain.model.MovieSummary summary) {
    URI self = linkTo(methodOn(MoviesApi.class).getMovie(summary.id().value())).toUri();
    MovieLinks links = new MovieLinks(new Link(self));

    return new com.acme.generated.model.MovieSummary(
            summary.id().value(), summary.title(), summary.releaseYear(), summary.genres(), links)
        .runtimeMinutes(summary.runtime().map(rm -> rm.value()).orElse(null))
        .rating(summary.rating().map(r -> r.value().stripTrailingZeros()).orElse(null));
  }
}
