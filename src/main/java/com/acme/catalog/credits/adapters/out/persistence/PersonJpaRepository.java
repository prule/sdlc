package com.acme.catalog.credits.adapters.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link PersonJpaEntity}. */
public interface PersonJpaRepository extends JpaRepository<PersonJpaEntity, UUID> {}
