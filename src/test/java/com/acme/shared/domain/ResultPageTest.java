package com.acme.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ResultPageTest {

  @ParameterizedTest
  @CsvSource({"0, 2, 0", "5, 2, 3", "25, 2, 13", "0, 20, 0", "5, 20, 1", "25, 20, 2", "20, 20, 1"})
  void totalPagesIsTheTotalDividedBySizeRoundedUp(long total, int size, int expectedPages) {
    assertThat(new ResultPage<>(List.of(), 0, size, total).totalPages()).isEqualTo(expectedPages);
  }

  @Test
  void firstOfSeveralPagesHasANextButNoPrevious() {
    ResultPage<String> page = new ResultPage<>(List.of("a", "b"), 0, 2, 5);

    assertThat(page.hasPrevious()).isFalse();
    assertThat(page.hasNext()).isTrue();
    assertThat(page.lastPage()).isEqualTo(2);
  }

  @Test
  void middlePageHasBothNeighbours() {
    ResultPage<String> page = new ResultPage<>(List.of("c", "d"), 1, 2, 5);

    assertThat(page.hasPrevious()).isTrue();
    assertThat(page.hasNext()).isTrue();
  }

  @Test
  void lastPageHasAPreviousButNoNext() {
    ResultPage<String> page = new ResultPage<>(List.of("e"), 2, 2, 5);

    assertThat(page.hasPrevious()).isTrue();
    assertThat(page.hasNext()).isFalse();
    assertThat(page.lastPage()).isEqualTo(2);
  }

  @Test
  void pageAfterTheLastHasNeitherNeighbourButKeepsTheTotals() {
    ResultPage<String> page = ResultPage.empty(new PageSpec(9, 2), 5);

    assertThat(page.items()).isEmpty();
    assertThat(page.totalElements()).isEqualTo(5);
    assertThat(page.totalPages()).isEqualTo(3);
    assertThat(page.hasPrevious()).isFalse();
    assertThat(page.hasNext()).isFalse();
  }

  @Test
  void emptyResultHasNoPagesAndLastPageZero() {
    ResultPage<String> page = ResultPage.empty(new PageSpec(0, 20), 0);

    assertThat(page.totalPages()).isZero();
    assertThat(page.lastPage()).isZero();
    assertThat(page.hasPrevious()).isFalse();
    assertThat(page.hasNext()).isFalse();
  }

  @Test
  void singlePageHasNoNeighbours() {
    ResultPage<String> page = new ResultPage<>(List.of("a"), 0, 20, 1);

    assertThat(page.hasPrevious()).isFalse();
    assertThat(page.hasNext()).isFalse();
  }
}
