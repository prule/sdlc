package com.acme.catalog.movies.adapters.in.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.acme.catalog.movies.application.port.in.GetMovieByIdUseCase;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.common.web.CorrelationId;
import com.acme.generated.api.MoviesApi;
import com.acme.generated.model.Link;
import com.acme.generated.model.Meta;
import com.acme.generated.model.MovieDetail;
import com.acme.generated.model.MovieDetailEnvelope;
import com.acme.generated.model.MovieLinks;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implements the generated {@link MoviesApi}. Thin: calls the {@link GetMovieByIdUseCase}, maps the
 * domain {@link Movie} to the generated {@link MovieDetail} DTO, and assembles the {@code self} HAL
 * link via {@link org.springframework.hateoas.server.mvc.WebMvcLinkBuilder}. No business logic
 * lives here; no HATEOAS import in domain/application.
 */
@RestController
public class MovieController implements MoviesApi {

  private final GetMovieByIdUseCase getMovieByIdUseCase;

  public MovieController(GetMovieByIdUseCase getMovieByIdUseCase) {
    this.getMovieByIdUseCase = getMovieByIdUseCase;
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
}
