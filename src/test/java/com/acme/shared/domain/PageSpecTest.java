package com.acme.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PageSpecTest {

  @ParameterizedTest
  @ValueSource(ints = {1, 100})
  void acceptsTheSizeBounds(int size) {
    assertThat(new PageSpec(0, size).size()).isEqualTo(size);
  }

  @Test
  void acceptsTheFirstPage() {
    assertThat(new PageSpec(0, 20).page()).isZero();
  }

  @Test
  void refusesANegativePageNamingPage() {
    assertThatThrownBy(() -> new PageSpec(-1, 20))
        .isInstanceOfSatisfying(
            InvalidRequestException.class, e -> assertThat(e.field()).isEqualTo("page"));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 101, -5})
  void refusesASizeOutOfBoundsNamingSize(int size) {
    assertThatThrownBy(() -> new PageSpec(0, size))
        .isInstanceOfSatisfying(
            InvalidRequestException.class, e -> assertThat(e.field()).isEqualTo("size"));
  }

  @Test
  void pageIsCheckedBeforeSize() {
    assertThatThrownBy(() -> new PageSpec(-1, 0))
        .isInstanceOfSatisfying(
            InvalidRequestException.class, e -> assertThat(e.field()).isEqualTo("page"));
  }

  @Test
  void offsetIsALongThatDoesNotOverflow() {
    assertThat(new PageSpec(Integer.MAX_VALUE, 100).offset()).isEqualTo(Integer.MAX_VALUE * 100L);
    assertThat(new PageSpec(2, 20).offset()).isEqualTo(40L);
  }
}
