package com.acme.catalog.movies.application.port.out;

import com.acme.catalog.movies.domain.model.MovieCredits;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port: load a movie's credits. Returns {@link Optional#empty()} ONLY when the movie
 * itself does not exist (BR-3); returns a present {@link MovieCredits} (possibly with empty
 * cast/crew) when the movie exists but has no credits recorded (BR-8). This distinction is the crux
 * of the not-found-vs-empty split — see design.md decision 3.
 */
public interface LoadMovieCreditsPort {

  Optional<MovieCredits> loadCreditsForMovie(UUID movieId);
}
