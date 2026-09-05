package com.acme.catalog.people.adapters.out.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link PersonJpaEntity}. Explicitly named to avoid a Spring bean-name
 * collision with {@code catalog.credits.adapters.out.persistence.PersonJpaRepository} — a distinct
 * repository (over its own {@code PersonJpaEntity}) that happens to share the same simple class
 * name in a different package.
 */
@Repository("catalogPeoplePersonJpaRepository")
public interface PersonJpaRepository extends JpaRepository<PersonJpaEntity, UUID> {

  /**
   * Searches People whose {@code name} case-insensitively matches {@code pattern}, sorted and paged
   * via {@code pageable}. {@code pattern} MUST already be wildcard-escaped and wrapped in {@code
   * %...%} by the caller (see {@code PersonSearchPersistenceAdapter#escapeLike}) — the explicit
   * {@code ESCAPE '\'} clause matches a literal {@code %}/{@code _}/{@code \} in a user-supplied
   * term rather than treating it as a SQL wildcard. Spring Data auto-derives the {@code COUNT}
   * query from this same {@code @Query}'s WHERE clause (so the {@code ESCAPE} applies to both the
   * page and the count), so a page load issues exactly two statements (page + count) regardless of
   * page size.
   */
  @Query("SELECT p FROM CatalogPeoplePerson p WHERE LOWER(p.name) LIKE LOWER(:pattern) ESCAPE '\\'")
  Page<PersonJpaEntity> searchByNamePattern(@Param("pattern") String pattern, Pageable pageable);
}
