package com.acme.catalog.credits.adapters.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link CreditJpaEntity}. {@link #findByMovieIdFetchingPersonOrdered}
 * is a single fetch-join query (credits → people) so loading a movie's credits issues one bounded
 * statement independent of credit count (see {@code openspec/changes/add-movie-credits/design.md},
 * D-D). Ordering ends in {@code c.id} — a unique terminal key — so results are total and stable.
 */
public interface CreditJpaRepository extends JpaRepository<CreditJpaEntity, UUID> {

  // The leading `c.creditType ASC` term is cosmetic only (it just clusters CAST rows before CREW
  // rows in the single result set) — the adapter partitions rows into cast/crew lists in Java
  // afterward, so cross-group order is irrelevant. Correctness of each list's *own* order comes
  // entirely from its per-type sort keys (billingOrder for CAST; department/job for CREW) plus the
  // shared, unique terminal key `c.id`, which is what guarantees a total, stable order even when
  // every other key ties.
  @Query(
      "SELECT c FROM CreditJpaEntity c JOIN FETCH c.person WHERE c.movieId = :movieId ORDER BY"
          + " c.creditType ASC, c.billingOrder ASC, LOWER(c.department) ASC, LOWER(c.job) ASC,"
          + " c.person.name ASC, c.id ASC")
  List<CreditJpaEntity> findByMovieIdFetchingPersonOrdered(@Param("movieId") UUID movieId);
}
