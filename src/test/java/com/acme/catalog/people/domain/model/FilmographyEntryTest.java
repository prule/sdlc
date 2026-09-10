package com.acme.catalog.people.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link FilmographyEntry} mapping of a movie summary plus one capacity. */
class FilmographyEntryTest {

  private static FilmographyMovieSummary movie() {
    return FilmographyMovieSummary.of(
        UUID.randomUUID(), "The Wandering Reel", 2019, List.of(Genre.DRAMA), 118, null);
  }

  @Test
  void entry_withActingCapacity_carriesBothMovieAndCapacity() {
    FilmographyMovieSummary movie = movie();
    Capacity.Acting acting = Capacity.Acting.of("Dana Whitfield");

    FilmographyEntry entry = new FilmographyEntry(movie, acting);

    assertThat(entry.movie()).isEqualTo(movie);
    assertThat(entry.capacity()).isEqualTo(acting);
  }

  @Test
  void entry_withNonActingCapacity_carriesBothMovieAndCapacity() {
    FilmographyMovieSummary movie = movie();
    Capacity.NonActing directing = new Capacity.NonActing("Directing", "Director");

    FilmographyEntry entry = new FilmographyEntry(movie, directing);

    assertThat(entry.movie()).isEqualTo(movie);
    assertThat(entry.capacity()).isEqualTo(directing);
  }

  @Test
  void entry_withNullMovie_throwsNullPointerException() {
    assertThatThrownBy(() -> new FilmographyEntry(null, Capacity.Acting.of(null)))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void entry_withNullCapacity_throwsNullPointerException() {
    assertThatThrownBy(() -> new FilmographyEntry(movie(), null))
        .isInstanceOf(NullPointerException.class);
  }
}
