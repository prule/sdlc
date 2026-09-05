package com.acme.catalog.people.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.catalog.people.domain.model.Person;
import com.acme.catalog.people.domain.model.PersonPage;
import com.acme.catalog.people.domain.model.PersonSearchCriteria;
import com.acme.catalog.people.domain.model.PersonSort;
import com.acme.catalog.people.domain.model.PersonSortField;
import com.acme.catalog.people.domain.model.SortDirection;
import com.acme.common.test.PostgresIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Testcontainers (real Postgres, no H2) test for {@link PersonSearchPersistenceAdapter}. Inserts
 * its own fixtures and runs inside a rolled-back transaction so nothing leaks into the shared
 * {@link PostgresIntegrationTest} container. Seed-independent of the {@code @Profile("demo")} data.
 */
@Transactional
class PersonSearchPersistenceAdapterTest extends PostgresIntegrationTest {

  @Autowired private PersonJpaRepository personJpaRepository;
  @Autowired private PersonSearchPersistenceAdapter searchAdapter;

  @Test
  void search_nameFilter_matchesCaseInsensitiveSubstringOnly() {
    UUID keanuId = savePerson("Keanu Reeves");
    savePerson("Unrelated Person");

    PersonPage page = search(criteriaWithName("KEANU"));

    assertThat(ids(page)).containsExactly(keanuId);
  }

  @Test
  void search_noNameFilter_returnsEveryone() {
    UUID a = savePerson("Alpha Person");
    UUID b = savePerson("Beta Person");

    PersonPage page = search(PersonSearchCriteria.NONE);

    assertThat(ids(page)).containsExactlyInAnyOrder(a, b);
  }

  @Test
  void search_nameFilterMatchingNothing_returnsEmptyNotAnError() {
    savePerson("Someone");

    PersonPage page = search(criteriaWithName("nonexistent-term"));

    assertThat(page.items()).isEmpty();
    assertThat(page.totalElements()).isZero();
  }

  @Test
  void search_nameSort_ordersAscendingAndDescending() {
    UUID b = savePerson("Beta");
    UUID a = savePerson("Alpha");
    UUID c = savePerson("Charlie");

    PersonPage asc =
        search(PersonSearchCriteria.NONE, new PersonSort(PersonSortField.NAME, SortDirection.ASC));
    assertThat(ids(asc)).containsExactly(a, b, c);

    PersonPage desc =
        search(PersonSearchCriteria.NONE, new PersonSort(PersonSortField.NAME, SortDirection.DESC));
    assertThat(ids(desc)).containsExactly(c, b, a);
  }

  @Test
  void search_defaultSort_isNameAscending() {
    UUID b = savePerson("Beta");
    UUID a = savePerson("Alpha");

    PersonPage page = search(PersonSearchCriteria.NONE, PersonSort.DEFAULT);

    assertThat(ids(page)).containsExactly(a, b);
  }

  @Test
  void search_pagination_returnsTheRequestedPageAndCorrectTotals() {
    savePerson("Alpha");
    savePerson("Beta");
    savePerson("Charlie");
    PersonSort nameAsc = new PersonSort(PersonSortField.NAME, SortDirection.ASC);

    PersonPage firstPage = searchAdapter.search(PersonSearchCriteria.NONE, 0, 2, nameAsc);
    assertThat(firstPage.items()).hasSize(2);
    assertThat(firstPage.totalElements()).isEqualTo(3);
    assertThat(firstPage.totalPages()).isEqualTo(2);

    PersonPage secondPage = searchAdapter.search(PersonSearchCriteria.NONE, 1, 2, nameAsc);
    assertThat(secondPage.items()).hasSize(1);
  }

  @Test
  void search_pageBeyondLastPage_isEmptyNotAnError() {
    savePerson("Alpha");

    PersonPage page = searchAdapter.search(PersonSearchCriteria.NONE, 9, 5, PersonSort.DEFAULT);

    assertThat(page.items()).isEmpty();
    assertThat(page.totalElements()).isEqualTo(1);
  }

  /**
   * Regression test for a non-deterministic-pagination bug: two rows sharing the same {@code name}
   * have an undefined relative order under {@code LIMIT}/{@code OFFSET} unless the ordering ends in
   * a unique terminal key ({@code id}). Without it, paging one row at a time across two same-named
   * rows can skip one and repeat the other.
   */
  @Test
  void search_pagesOfPeopleSharingTheSameName_areDeterministicWithNoSkipOrDuplicate() {
    UUID first = savePerson("Same Name");
    UUID second = savePerson("Same Name");

    PersonPage page0 = searchAdapter.search(PersonSearchCriteria.NONE, 0, 1, PersonSort.DEFAULT);
    PersonPage page1 = searchAdapter.search(PersonSearchCriteria.NONE, 1, 1, PersonSort.DEFAULT);

    List<UUID> seenIds = new ArrayList<>();
    seenIds.addAll(ids(page0));
    seenIds.addAll(ids(page1));

    assertThat(seenIds).as("no skip, no duplicate across the two pages").hasSize(2);
    assertThat(seenIds).containsExactlyInAnyOrder(first, second);
    assertThat(page0.items()).hasSize(1);
    assertThat(page1.items()).hasSize(1);
  }

  /**
   * Regression test for a missing {@code ESCAPE} clause on the name {@code LIKE} filter: a search
   * term containing literal {@code %} and {@code _} must be matched literally, not treated as SQL
   * wildcards.
   */
  @Test
  void search_nameContainingWildcardCharacters_matchesOnlyThatLiteralTerm() {
    UUID literal = savePerson("100%_Real Person");
    savePerson("100 Real XPerson");
    savePerson("100XReal Person");

    PersonPage page = search(criteriaWithName("100%_Real"));

    assertThat(ids(page)).containsExactly(literal);
    // The count query derives from the same escaped @Query, so totalElements/totalPages must also
    // reflect only the literal match — not the over-matching an unescaped %/_ would produce.
    assertThat(page.totalElements()).isEqualTo(1);
    assertThat(page.totalPages()).isEqualTo(1);
  }

  private UUID savePerson(String name) {
    UUID personId = UUID.randomUUID();
    personJpaRepository.save(new PersonJpaEntity(personId, name));
    return personId;
  }

  private static PersonSearchCriteria criteriaWithName(String name) {
    return new PersonSearchCriteria(Optional.of(name));
  }

  private PersonPage search(PersonSearchCriteria criteria) {
    return search(criteria, PersonSort.DEFAULT);
  }

  private PersonPage search(PersonSearchCriteria criteria, PersonSort sort) {
    return searchAdapter.search(criteria, 0, 20, sort);
  }

  private static List<UUID> ids(PersonPage page) {
    return page.items().stream().map(Person::id).map(id -> id.value()).toList();
  }
}
