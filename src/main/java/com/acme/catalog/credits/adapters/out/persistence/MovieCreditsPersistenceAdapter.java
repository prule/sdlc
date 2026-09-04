package com.acme.catalog.credits.adapters.out.persistence;

import com.acme.catalog.credits.application.port.out.LoadMovieCreditsPort;
import com.acme.catalog.credits.domain.model.CastCredit;
import com.acme.catalog.credits.domain.model.Credit;
import com.acme.catalog.credits.domain.model.CrewCredit;
import com.acme.catalog.credits.domain.model.MovieCredits;
import com.acme.catalog.credits.domain.model.Person;
import com.acme.catalog.credits.domain.model.PersonId;
import com.acme.catalog.movies.adapters.out.persistence.MovieJpaRepository;
import com.acme.catalog.movies.domain.model.MovieId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Implements {@link LoadMovieCreditsPort} against Postgres via {@link CreditJpaRepository}, mapping
 * JPA rows to/from domain and partitioning the single ordered result set by {@code credit_type}.
 * {@code Optional.empty()} is returned only when the {@code movies} row itself is absent (a
 * lightweight existence probe) — a movie with zero credits still returns a present, empty {@link
 * MovieCredits}. Together the probe and the fetch-join query are two bounded statements,
 * independent of credit count. The domain is never annotated {@code @Entity}.
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
  public Optional<MovieCredits> load(MovieId id) {
    if (!movieJpaRepository.existsById(id.value())) {
      return Optional.empty();
    }

    List<CreditJpaEntity> rows = creditJpaRepository.findByMovieIdFetchingPersonOrdered(id.value());

    List<CastCredit> cast = new ArrayList<>();
    List<CrewCredit> crew = new ArrayList<>();
    for (CreditJpaEntity row : rows) {
      Credit credit = toDomain(row);
      if (credit instanceof CastCredit castCredit) {
        cast.add(castCredit);
      } else if (credit instanceof CrewCredit crewCredit) {
        crew.add(crewCredit);
      }
    }

    return Optional.of(new MovieCredits(cast, crew));
  }

  private static Credit toDomain(CreditJpaEntity row) {
    Person person = toDomain(row.getPerson());
    return switch (row.getCreditType()) {
      case CAST -> new CastCredit(person, row.getCharacterName(), row.getBillingOrder());
      case CREW -> new CrewCredit(person, row.getDepartment(), row.getJob());
    };
  }

  private static Person toDomain(PersonJpaEntity entity) {
    return new Person(new PersonId(entity.getId()), entity.getName());
  }
}
