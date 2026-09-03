package com.acme.catalog.movies.application.port.out;

import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieId;
import java.util.Optional;

/** Outbound port: load a Movie by its stable opaque id, from wherever it is persisted. */
public interface LoadMovieByIdPort {

  Optional<Movie> load(MovieId id);
}
