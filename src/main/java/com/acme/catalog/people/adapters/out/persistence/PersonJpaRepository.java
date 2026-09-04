package com.acme.catalog.people.adapters.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link PersonJpaEntity}. Explicitly named to avoid a Spring bean-name
 * collision with {@code catalog.credits.adapters.out.persistence.PersonJpaRepository} — a distinct
 * repository (over its own {@code PersonJpaEntity}) that happens to share the same simple class
 * name in a different package.
 */
@Repository("catalogPeoplePersonJpaRepository")
public interface PersonJpaRepository extends JpaRepository<PersonJpaEntity, UUID> {}
