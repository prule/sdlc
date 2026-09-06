package com.acme.catalog.movies.adapters.in.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.acme.catalog.movies.application.port.in.GetMovieDetailUseCase;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.common.web.CorrelationId;
import com.acme.generated.api.MoviesApi;
import com.acme.generated.model.Genre;
import com.acme.generated.model.Link;
import com.acme.generated.model.Meta;
import com.acme.generated.model.MovieDetail;
import com.acme.generated.model.MovieDetailEnvelope;
import com.acme.generated.model.MovieLinks;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implements the generated {@link MoviesApi}. Thin: calls the {@link GetMovieDetailUseCase}, maps
 * the domain {@link Movie} to the generated {@link MovieDetailEnvelope}, and builds {@code
 * data._links.self} via {@link org.springframework.hateoas.server.mvc.WebMvcLinkBuilder}. Link
 * assembly is web-adapter-only — the domain and application layers know nothing about hypermedia.
 */
@RestController
public class MovieController implements MoviesApi {

  private final GetMovieDetailUseCase getMovieDetailUseCase;

  public MovieController(GetMovieDetailUseCase getMovieDetailUseCase) {
    this.getMovieDetailUseCase = getMovieDetailUseCase;
  }

  @Override
  public ResponseEntity<MovieDetailEnvelope> getMovieById(UUID id, UUID xCorrelationId) {
    Movie movie = getMovieDetailUseCase.getMovieDetail(id);
    String correlationId = CorrelationId.current();

    MovieDetail data = toMovieDetail(movie);
    Meta meta = new Meta(OffsetDateTime.now(ZoneOffset.UTC), UUID.fromString(correlationId));

    return ResponseEntity.ok(new MovieDetailEnvelope(data, meta));
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

  private static Link selfLink(UUID id) {
    return new Link(linkTo(methodOn(MoviesApi.class).getMovieById(id, null)).toUri());
  }
}
