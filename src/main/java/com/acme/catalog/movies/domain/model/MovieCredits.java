package com.acme.catalog.movies.domain.model;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A movie's full credits, split into {@link Credit.Cast} and {@link Credit.Crew}. Constructed only
 * via {@link #of}, which enforces ordering as the domain's responsibility (design.md decision 2):
 * cast ordered by {@code billingOrder} ascending (BR-5), crew ordered by {@code department} then
 * {@code job} ascending, both compared case-insensitively (BR-6). An empty cast and/or crew is
 * valid (BR-8). No Spring/JPA/HAL imports — see standards/clean-architecture.md.
 */
public record MovieCredits(List<Credit.Cast> cast, List<Credit.Crew> crew) {

  private static final Comparator<Credit.Cast> BY_BILLING_ORDER =
      Comparator.comparingInt(Credit.Cast::billingOrder);

  private static final Comparator<Credit.Crew> BY_DEPARTMENT_THEN_JOB =
      Comparator.<Credit.Crew, String>comparing(crew -> crew.department().toLowerCase(Locale.ROOT))
          .thenComparing(crew -> crew.job().toLowerCase(Locale.ROOT));

  public MovieCredits {
    Objects.requireNonNull(cast, "cast must not be null");
    Objects.requireNonNull(crew, "crew must not be null");
    cast = List.copyOf(cast);
    crew = List.copyOf(crew);
  }

  /** Creates a {@link MovieCredits}, ordering cast and crew per the domain's rules. */
  public static MovieCredits of(List<Credit.Cast> cast, List<Credit.Crew> crew) {
    return new MovieCredits(
        cast.stream().sorted(BY_BILLING_ORDER).toList(),
        crew.stream().sorted(BY_DEPARTMENT_THEN_JOB).toList());
  }
}
