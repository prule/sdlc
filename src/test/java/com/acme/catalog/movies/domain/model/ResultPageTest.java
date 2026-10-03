package com.acme.catalog.movies.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ResultPageTest {

  private static ResultPage<String> page(int page, int size, long total) {
    return new ResultPage<>(List.of(), page, size, total);
  }

  @Test
  void anEmptyResultHasNoPagesAndOnlyTheFirstPageToPointTo() {
    ResultPage<String> empty = page(0, 20, 0);

    assertThat(empty.totalPages()).isZero();
    assertThat(empty.lastPage()).isZero();
    assertThat(empty.hasPrev()).isFalse();
    assertThat(empty.hasNext()).isFalse();
  }

  @Test
  void roundsTheNumberOfPagesUp() {
    assertThat(page(0, 20, 40).totalPages()).isEqualTo(2);
    assertThat(page(0, 20, 45).totalPages()).isEqualTo(3);
    assertThat(page(0, 20, 45).lastPage()).isEqualTo(2);
  }

  @Test
  void theFirstPageHasNoPrevAndTheLastHasNoNext() {
    assertThat(page(0, 20, 45).hasPrev()).isFalse();
    assertThat(page(0, 20, 45).hasNext()).isTrue();
    assertThat(page(1, 20, 45).hasPrev()).isTrue();
    assertThat(page(1, 20, 45).hasNext()).isTrue();
    assertThat(page(2, 20, 45).hasPrev()).isTrue();
    assertThat(page(2, 20, 45).hasNext()).isFalse();
  }

  @Test
  void thePageJustAfterTheLastPointsBackButAFarLaterPageDoesNot() {
    assertThat(page(3, 20, 45).hasPrev()).isTrue();
    assertThat(page(3, 20, 45).hasNext()).isFalse();
    assertThat(page(7, 20, 45).hasPrev()).isFalse();
    assertThat(page(7, 20, 45).hasNext()).isFalse();
  }

  @Test
  void handlesAPageFarBeyondTheIntegerRangeOfEntries() {
    ResultPage<String> far = page(Integer.MAX_VALUE, 100, 45);

    assertThat(far.lastPage()).isZero();
    assertThat(far.hasPrev()).isFalse();
  }
}
