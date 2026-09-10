package com.acme.catalog.people.adapters.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link PersonDetailJpaEntity}. */
public interface PersonDetailJpaRepository extends JpaRepository<PersonDetailJpaEntity, UUID> {}
