package com.acme.catalog.movies.adapters.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link MovieJpaEntity} (design D5). */
public interface MovieJpaRepository extends JpaRepository<MovieJpaEntity, UUID> {

  @Override
  @EntityGraph(attributePaths = "genres")
  Optional<MovieJpaEntity> findById(UUID id);

  /** Hydrates a page of movies, with their genres, in one query (design D5). Order unspecified. */
  @EntityGraph(attributePaths = "genres")
  List<MovieJpaEntity> findAllByIdIn(Collection<UUID> ids);
}
