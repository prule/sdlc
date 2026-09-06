package com.acme.catalog.movies.adapters.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link MovieJpaEntity}. */
public interface MovieJpaRepository extends JpaRepository<MovieJpaEntity, UUID> {}
