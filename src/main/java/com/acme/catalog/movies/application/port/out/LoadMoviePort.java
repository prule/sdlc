package com.acme.catalog.movies.application.port.out;

import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieId;
import java.util.Optional;

/** Outbound port for loading a curated movie by id (UC-001). */
public interface LoadMoviePort {

  Optional<Movie> loadMovie(MovieId id);
}
