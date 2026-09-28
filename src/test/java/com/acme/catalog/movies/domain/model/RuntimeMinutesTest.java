package com.acme.catalog.movies.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RuntimeMinutesTest {

  @Test
  void acceptsAPositiveValue() {
    RuntimeMinutes runtime = new RuntimeMinutes(116);

    assertThat(runtime.value()).isEqualTo(116);
  }

  @Test
  void rejectsZero() {
    assertThatThrownBy(() -> new RuntimeMinutes(0)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsANegativeValue() {
    assertThatThrownBy(() -> new RuntimeMinutes(-1)).isInstanceOf(IllegalArgumentException.class);
  }
}
