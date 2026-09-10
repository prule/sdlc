package com.acme.catalog.movies.adapters.out.persistence;

import com.acme.catalog.movies.application.port.out.LoadMovieCreditsPort;
import com.acme.catalog.movies.domain.model.Credit;
import com.acme.catalog.movies.domain.model.MovieCredits;
import com.acme.catalog.movies.domain.model.Person;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implements {@link LoadMovieCreditsPort}. Checks movie existence first (reusing {@link
 * MovieJpaRepository}) so an unknown movie yields {@link Optional#empty()}, distinct from an
 * existing movie with no credits (BR-3 vs BR-8, design.md decision 3), then loads and maps that
 * movie's credit rows to the domain. Ordering is re-asserted by {@link MovieCredits#of}, so query
 * order is not relied upon.
 */
@Component
public class MovieCreditsPersistenceAdapter implements LoadMovieCreditsPort {

  private final MovieJpaRepository movieJpaRepository;
  private final CreditJpaRepository creditJpaRepository;

  public MovieCreditsPersistenceAdapter(
      MovieJpaRepository movieJpaRepository, CreditJpaRepository creditJpaRepository) {
    this.movieJpaRepository = movieJpaRepository;
    this.creditJpaRepository = creditJpaRepository;
  }

  @Override
  public Optional<MovieCredits> loadCreditsForMovie(UUID movieId) {
    if (!movieJpaRepository.existsById(movieId)) {
      return Optional.empty();
    }

    List<CreditJpaEntity> rows = creditJpaRepository.findByMovieId(movieId);
    List<Credit.Cast> cast = new ArrayList<>();
    List<Credit.Crew> crew = new ArrayList<>();

    for (CreditJpaEntity row : rows) {
      Person person = new Person(row.getPerson().getId(), row.getPerson().getName());
      switch (row.getKind()) {
        case CAST -> cast.add(Credit.Cast.of(person, row.getCharacter(), row.getBillingOrder()));
        case CREW -> crew.add(new Credit.Crew(person, row.getDepartment(), row.getJob()));
      }
    }

    return Optional.of(MovieCredits.of(cast, crew));
  }
}
