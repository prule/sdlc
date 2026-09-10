package com.acme.catalog.movies.adapters.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link CreditJpaEntity}. Uses a derived query (no native SQL — see
 * standards/clean-architecture.md and CLAUDE.md's H2/Postgres uuid-portability rule, which only
 * applies to native queries).
 */
public interface CreditJpaRepository extends JpaRepository<CreditJpaEntity, UUID> {

  List<CreditJpaEntity> findByMovieId(UUID movieId);
}
