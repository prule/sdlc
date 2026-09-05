package com.acme.catalog.people.application.service;

import com.acme.catalog.people.application.port.in.SearchPeopleUseCase;
import com.acme.catalog.people.application.port.out.SearchPeoplePort;
import com.acme.catalog.people.domain.model.PersonPage;
import com.acme.catalog.people.domain.model.PersonSearchCriteria;
import com.acme.catalog.people.domain.model.PersonSort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the search-people use case by delegating straight through to {@link SearchPeoplePort}.
 * {@code @Transactional(readOnly = true)}: the port issues the page and count as separate queries
 * (see {@code PersonSearchPersistenceAdapter}), so a read-only transaction gives them a consistent
 * snapshot and states the read-only intent for future authors copying this pattern.
 */
@Service
public class SearchPeopleService implements SearchPeopleUseCase {

  private final SearchPeoplePort searchPeoplePort;

  public SearchPeopleService(SearchPeoplePort searchPeoplePort) {
    this.searchPeoplePort = searchPeoplePort;
  }

  @Override
  @Transactional(readOnly = true)
  public PersonPage searchPeople(
      PersonSearchCriteria criteria, int page, int size, PersonSort sort) {
    return searchPeoplePort.search(criteria, page, size, sort);
  }
}
