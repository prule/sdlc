package com.acme.catalog.people.adapters.out.persistence;

import com.acme.catalog.people.application.port.out.SearchPeoplePort;
import com.acme.catalog.people.domain.model.PersonPage;
import com.acme.catalog.people.domain.model.PersonSearchCriteria;
import com.acme.catalog.people.domain.model.PersonSort;
import com.acme.catalog.people.domain.model.SortDirection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Implements {@link SearchPeoplePort} against Postgres via the reused {@link PersonJpaRepository}
 * (no second entity/repository) using Spring Data's paged {@code @Query} support (see {@code
 * openspec/changes/add-person-search/design.md}, D3): a Person has no to-many associations, so
 * there is no N+1 risk from lazy loading — a single page query (whose {@code COUNT} Spring Data
 * auto-derives from the same {@code @Query} WHERE clause, so {@code ESCAPE} applies to both) is
 * bounded and independent of page size, exactly two statements per page.
 */
@Component
public class PersonSearchPersistenceAdapter implements SearchPeoplePort {

  private final PersonJpaRepository personJpaRepository;

  public PersonSearchPersistenceAdapter(PersonJpaRepository personJpaRepository) {
    this.personJpaRepository = personJpaRepository;
  }

  @Override
  public PersonPage search(PersonSearchCriteria criteria, int page, int size, PersonSort sort) {
    String pattern = "%" + escapeLike(criteria.name().orElse("")) + "%";
    Pageable pageable = PageRequest.of(page, size, orderBy(sort));

    Page<PersonJpaEntity> result = personJpaRepository.searchByNamePattern(pattern, pageable);

    return new PersonPage(
        result.getContent().stream().map(PersonPersistenceAdapter::toDomain).toList(),
        page,
        size,
        result.getTotalElements(),
        result.getTotalPages());
  }

  /**
   * Escapes LIKE metacharacters ({@code \}, {@code %}, {@code _}) in a user-supplied search term so
   * it is matched literally when wrapped in {@code %...%} and paired with {@code ESCAPE '\'}. The
   * backslash itself must be escaped first, or escaping it after {@code %}/{@code _} would
   * double-escape the backslashes just introduced. Mirrors {@code
   * MovieSearchPersistenceAdapter#escapeLike}.
   */
  static String escapeLike(String term) {
    return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  /**
   * Orders results by the requested field/direction, then the unique {@code id} ascending as the
   * final terminal tiebreak, so the order is total and stable and pagination cannot skip or
   * duplicate across pages (the CAT-002 lesson).
   */
  private static Sort orderBy(PersonSort sort) {
    Sort.Direction direction =
        sort.direction() == SortDirection.DESC ? Sort.Direction.DESC : Sort.Direction.ASC;
    return Sort.by(direction, "name").and(Sort.by(Sort.Direction.ASC, "id"));
  }
}
