package com.acme.catalog.movies.adapters.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for {@link MovieJpaEntity}. */
public interface MovieJpaRepository extends JpaRepository<MovieJpaEntity, UUID> {

  /**
   * Hydrates exactly the given ids with their genres in a single fetch-join query, avoiding a
   * per-row genre select (N+1). Callers reorder the result to match their own ordering — this query
   * makes no ordering guarantee.
   */
  @Query("select distinct m from MovieJpaEntity m left join fetch m.genres where m.id in :ids")
  List<MovieJpaEntity> findAllWithGenresByIdIn(@Param("ids") List<UUID> ids);
}
