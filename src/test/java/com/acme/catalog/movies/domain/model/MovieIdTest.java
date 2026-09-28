package com.acme.catalog.movies.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MovieIdTest {

  @Test
  void wrapsAValidUuid() {
    UUID uuid = UUID.randomUUID();

    MovieId id = new MovieId(uuid);

    assertThat(id.value()).isEqualTo(uuid);
  }

  @Test
  void rejectsANullUuid() {
    assertThatThrownBy(() -> new MovieId(null)).isInstanceOf(NullPointerException.class);
  }
}
