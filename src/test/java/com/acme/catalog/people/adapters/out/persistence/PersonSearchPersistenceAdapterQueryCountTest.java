package com.acme.catalog.people.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.catalog.people.domain.model.PersonPage;
import com.acme.catalog.people.domain.model.PersonSearchCriteria;
import com.acme.catalog.people.domain.model.PersonSort;
import com.acme.common.test.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Testcontainers (real Postgres) guard for the bounded-query decision (see {@code
 * openspec/changes/add-person-search/design.md}, D3): loading a page of person summaries must issue
 * a SQL statement count that is bounded and independent of page size — a Person has no to-many
 * associations, so no N+1 risk exists here — and the CAT-004 person-detail path must not regress.
 */
@Transactional
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class PersonSearchPersistenceAdapterQueryCountTest extends PostgresIntegrationTest {

  @Autowired private PersonJpaRepository personJpaRepository;
  @Autowired private PersonSearchPersistenceAdapter searchAdapter;
  @Autowired private PersonPersistenceAdapter personPersistenceAdapter;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @PersistenceContext private EntityManager entityManager;

  @Test
  void search_pageLoad_statementCountIsBoundedAndIndependentOfPageSize() {
    seedPeople(5);
    entityManager.flush();
    Statistics statistics = statistics();
    statistics.clear();

    PersonPage smallPage =
        searchAdapter.search(PersonSearchCriteria.NONE, 0, 5, PersonSort.DEFAULT);
    long smallPageStatementCount = statistics.getPrepareStatementCount();

    assertThat(smallPage.items()).hasSize(5);
    assertThat(smallPageStatementCount)
        .as("statement count for a 5-row page")
        .isLessThanOrEqualTo(2);

    seedPeople(20);
    entityManager.flush();
    statistics.clear();

    PersonPage largerPage =
        searchAdapter.search(PersonSearchCriteria.NONE, 0, 25, PersonSort.DEFAULT);
    long largerPageStatementCount = statistics.getPrepareStatementCount();

    assertThat(largerPage.items()).hasSize(25);
    assertThat(largerPageStatementCount)
        .as("statement count does not grow with page size")
        .isEqualTo(smallPageStatementCount);
  }

  @Test
  void loadPersonById_queryBehaviourDoesNotRegress() {
    UUID personId = UUID.randomUUID();
    personJpaRepository.save(new PersonJpaEntity(personId, "Detail Person"));
    entityManager.flush();
    Statistics statistics = statistics();
    statistics.clear();

    personPersistenceAdapter.load(new com.acme.catalog.people.domain.model.PersonId(personId));

    assertThat(statistics.getPrepareStatementCount())
        .as("statement count for the single-person detail path")
        .isLessThanOrEqualTo(1);
  }

  private Statistics statistics() {
    return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
  }

  private void seedPeople(int count) {
    for (int i = 0; i < count; i++) {
      personJpaRepository.save(
          new PersonJpaEntity(UUID.randomUUID(), "Person " + UUID.randomUUID()));
    }
  }
}
