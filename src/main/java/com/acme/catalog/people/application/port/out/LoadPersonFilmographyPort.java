package com.acme.catalog.people.application.port.out;

import com.acme.catalog.people.domain.model.Filmography;
import com.acme.catalog.people.domain.model.FilmographyCriteria;
import com.acme.catalog.people.domain.model.FilmographyPageRequest;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port: load one page of a person's filmography. Distinguishes an unknown person ({@link
 * Optional#empty()}) from a known person with an empty result page (a present {@link Filmography}
 * whose {@code content} is empty) — mirroring {@code
 * com.acme.catalog.movies.application.port.out.LoadMovieCreditsPort}.
 */
public interface LoadPersonFilmographyPort {

  Optional<Filmography> loadFilmography(
      UUID personId, FilmographyCriteria criteria, FilmographyPageRequest pageRequest);
}
