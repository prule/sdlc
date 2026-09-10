package com.acme.catalog.people.application.port.in;

import com.acme.catalog.people.domain.model.Filmography;
import com.acme.catalog.people.domain.model.FilmographyCriteria;
import com.acme.catalog.people.domain.model.FilmographyPageRequest;
import java.util.UUID;

/** Inbound port: retrieve one page of a person's filmography, optionally filtered (UC-005). */
public interface GetPersonFilmographyUseCase {

  /**
   * Returns one page of the filmography of the person identified by {@code personId}.
   *
   * @throws com.acme.common.error.ResourceNotFoundException if no person matches {@code personId}
   */
  Filmography getFilmography(
      UUID personId, FilmographyCriteria criteria, FilmographyPageRequest pageRequest);
}
