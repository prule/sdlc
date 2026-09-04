package com.acme.catalog.credits.application.port.out;

import com.acme.catalog.credits.domain.model.MovieCredits;
import com.acme.catalog.movies.domain.model.MovieId;
import java.util.Optional;

/**
 * Outbound port: load a Movie's cast and crew, from wherever it is persisted.
 *
 * <p>{@code Optional.empty()} distinguishes an <b>unknown movie</b> (no {@code movies} row) from a
 * <b>movie with no credits</b> (a present {@link MovieCredits} with empty cast/crew lists) — see
 * {@code openspec/changes/add-movie-credits/design.md}, D-C.
 */
public interface LoadMovieCreditsPort {

  Optional<MovieCredits> load(MovieId id);
}
