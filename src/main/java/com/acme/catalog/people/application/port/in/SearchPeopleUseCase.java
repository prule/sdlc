package com.acme.catalog.people.application.port.in;

import com.acme.catalog.people.domain.model.PersonPage;
import com.acme.catalog.people.domain.model.PersonSearchCriteria;
import com.acme.catalog.people.domain.model.PersonSort;

/** Inbound port: search a page of Person summaries matching the given filter, sorted and paged. */
public interface SearchPeopleUseCase {

  /**
   * @param criteria the filter to apply ({@code name}, the only supported filter)
   * @param page zero-based page index
   * @param size page size
   * @param sort the field/direction to sort by
   * @return the requested page of matching people plus page metadata
   */
  PersonPage searchPeople(PersonSearchCriteria criteria, int page, int size, PersonSort sort);
}
