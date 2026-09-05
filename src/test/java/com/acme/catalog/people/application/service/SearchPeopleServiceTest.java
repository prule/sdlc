package com.acme.catalog.people.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.acme.catalog.people.application.port.out.SearchPeoplePort;
import com.acme.catalog.people.domain.model.Person;
import com.acme.catalog.people.domain.model.PersonId;
import com.acme.catalog.people.domain.model.PersonPage;
import com.acme.catalog.people.domain.model.PersonSearchCriteria;
import com.acme.catalog.people.domain.model.PersonSort;
import com.acme.catalog.people.domain.model.PersonSortField;
import com.acme.catalog.people.domain.model.SortDirection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SearchPeopleServiceTest {

  private final SearchPeoplePort searchPeoplePort = mock(SearchPeoplePort.class);
  private final SearchPeopleService service = new SearchPeopleService(searchPeoplePort);

  @Test
  void searchPeople_delegatesCriteriaPageSizeSortAndReturnsThePortResult() {
    PersonSearchCriteria criteria = new PersonSearchCriteria(Optional.of("keanu"));
    PersonSort sort = new PersonSort(PersonSortField.NAME, SortDirection.DESC);
    Person person = new Person(new PersonId(UUID.randomUUID()), "Keanu Reeves");
    PersonPage expectedPage = new PersonPage(List.of(person), 2, 10, 1, 1);
    given(searchPeoplePort.search(criteria, 2, 10, sort)).willReturn(expectedPage);

    PersonPage result = service.searchPeople(criteria, 2, 10, sort);

    assertThat(result).isEqualTo(expectedPage);
  }
}
