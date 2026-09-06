package com.acme.catalog.movies.application.port.out;

import com.acme.catalog.movies.domain.model.Movie;
import java.util.Optional;
import java.util.UUID;

/** Outbound port: load one movie by its stable identifier. */
public interface LoadMoviePort {

  Optional<Movie> loadById(UUID id);
}
