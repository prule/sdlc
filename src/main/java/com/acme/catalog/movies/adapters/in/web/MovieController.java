package com.acme.catalog.movies.adapters.in.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.acme.catalog.movies.application.port.in.GetMovieUseCase;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.generated.api.MoviesApi;
import com.acme.generated.model.Link;
import com.acme.generated.model.MovieDetail;
import com.acme.generated.model.MovieEnvelope;
import com.acme.generated.model.MovieLinks;
import com.acme.platform.web.ResponseMetaFactory;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Retrieves one movie's curated details ({@code GET /movies/{id}}, UC-001). Content-Type is always
 * {@code application/json}, never negotiated to {@code application/problem+json} (design D1/D3).
 */
@RestController
public class MovieController implements MoviesApi {

  private final GetMovieUseCase getMovieUseCase;
  private final ResponseMetaFactory responseMetaFactory;

  public MovieController(GetMovieUseCase getMovieUseCase, ResponseMetaFactory responseMetaFactory) {
    this.getMovieUseCase = getMovieUseCase;
    this.responseMetaFactory = responseMetaFactory;
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
}
