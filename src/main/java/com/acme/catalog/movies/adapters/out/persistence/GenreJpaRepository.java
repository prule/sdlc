package com.acme.catalog.movies.adapters.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link GenreJpaEntity}. */
public interface GenreJpaRepository extends JpaRepository<GenreJpaEntity, UUID> {}
