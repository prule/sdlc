package com.acme.catalog.people.application.port.in;

import com.acme.catalog.people.domain.model.FilmographyPage;
import com.acme.catalog.people.domain.model.PersonId;

/** Inbound port: retrieve a page of a single Person's filmography by their stable opaque id. */
public interface GetPersonFilmographyUseCase {

  /**
   * @throws com.acme.common.error.ResourceNotFoundException if no Person exists for {@code id}. A
   *     Person that exists but has no credits returns a present, empty {@link FilmographyPage}, not
   *     an exception.
   */
  FilmographyPage getPersonFilmography(PersonId id, int page, int size);
}
