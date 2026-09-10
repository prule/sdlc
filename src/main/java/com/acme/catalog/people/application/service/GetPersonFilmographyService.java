package com.acme.catalog.people.application.service;

import com.acme.catalog.people.application.port.in.GetPersonFilmographyUseCase;
import com.acme.catalog.people.application.port.out.LoadPersonFilmographyPort;
import com.acme.catalog.people.domain.model.Filmography;
import com.acme.catalog.people.domain.model.FilmographyCriteria;
import com.acme.catalog.people.domain.model.FilmographyPageRequest;
import com.acme.common.error.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Implements the get-person-filmography use case. A well-formed identifier that matches no person
 * is reported as a {@link ResourceNotFoundException} with the stable code {@code PERSON_NOT_FOUND}
 * (reusing UC-004's code), handled by the existing global exception handler — no bespoke exception,
 * no new handler. An existing person with no credits, a filter matching nothing, or a page beyond
 * the last all delegate straight through as a normal (empty) {@link Filmography}.
 */
@Service
public class GetPersonFilmographyService implements GetPersonFilmographyUseCase {

  private final LoadPersonFilmographyPort loadPersonFilmographyPort;

  public GetPersonFilmographyService(LoadPersonFilmographyPort loadPersonFilmographyPort) {
    this.loadPersonFilmographyPort = loadPersonFilmographyPort;
  }

  @Override
  public Filmography getFilmography(
      UUID personId, FilmographyCriteria criteria, FilmographyPageRequest pageRequest) {
    return loadPersonFilmographyPort
        .loadFilmography(personId, criteria, pageRequest)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "PERSON_NOT_FOUND", "No person found with id " + personId));
  }
}
