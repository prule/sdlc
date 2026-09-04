package com.acme.catalog.credits.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * The full cast and crew of a single Movie, each list already in its final, total, stable order
 * (see {@code openspec/changes/add-movie-credits/design.md}, D-D): {@code cast} by {@code
 * billingOrder} ascending, {@code crew} by {@code department} then {@code job} ascending — both
 * ending in a deterministic terminal tiebreak. Either list may be empty (a Movie with no cast
 * and/or no crew); this is distinct from the Movie itself not existing, which the outbound port
 * signals with {@code Optional.empty()} rather than an empty {@code MovieCredits}.
 */
public record MovieCredits(List<CastCredit> cast, List<CrewCredit> crew) {

  public MovieCredits {
    Objects.requireNonNull(cast, "cast must not be null");
    Objects.requireNonNull(crew, "crew must not be null");
    cast = List.copyOf(cast);
    crew = List.copyOf(crew);
  }
}
