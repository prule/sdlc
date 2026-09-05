package com.acme.catalog.people.application.service;

import com.acme.catalog.people.application.port.in.GetPersonFilmographyUseCase;
import com.acme.catalog.people.application.port.out.PersonFilmographyPort;
import com.acme.catalog.people.domain.model.FilmographyPage;
import com.acme.catalog.people.domain.model.PersonId;
import com.acme.common.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the get-person-filmography use case. An unknown person is a domain-level 404, reusing
 * the same {@code PERSON_NOT_FOUND} code as {@code GetPersonByIdService}; a person with no credits
 * is a normal, present, empty page.
 */
@Service
public class GetPersonFilmographyService implements GetPersonFilmographyUseCase {

  private final PersonFilmographyPort personFilmographyPort;

  public GetPersonFilmographyService(PersonFilmographyPort personFilmographyPort) {
    this.personFilmographyPort = personFilmographyPort;
  }

  @Override
  @Transactional(readOnly = true)
  public FilmographyPage getPersonFilmography(PersonId id, int page, int size) {
    return personFilmographyPort
        .loadFilmography(id, page, size)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "PERSON_NOT_FOUND", "No person found for id: " + id.value()));
  }
}
