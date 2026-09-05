package com.acme.catalog.people.application.port.out;

import com.acme.catalog.people.domain.model.FilmographyPage;
import com.acme.catalog.people.domain.model.PersonId;
import java.util.Optional;

/**
 * Outbound port: load a page of a Person's filmography, from wherever it is persisted.
 *
 * <p>{@code Optional.empty()} distinguishes an <b>unknown person</b> (no {@code people} row) from a
 * <b>person with no credits</b> (a present, empty {@link FilmographyPage}) — mirrors {@code
 * LoadMovieCreditsPort} (see {@code openspec/changes/add-person-filmography/design.md}, D-D).
 */
public interface PersonFilmographyPort {

  /**
   * @param id the Person's stable opaque id
   * @param page zero-based page index
   * @param size page size
   * @return the requested page of the Person's filmography plus page metadata, or {@code
   *     Optional.empty()} if no such Person exists
   */
  Optional<FilmographyPage> loadFilmography(PersonId id, int page, int size);
}
