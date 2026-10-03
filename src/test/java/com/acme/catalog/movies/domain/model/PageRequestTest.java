package com.acme.catalog.movies.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.shared.domain.InvalidCriteriaException;
import com.acme.shared.domain.InvalidCriteriaException.Violation;
import org.junit.jupiter.api.Test;

class PageRequestTest {

  @Test
  void defaultsToTheFirstPageOfTwenty() {
    assertThat(PageRequest.of(null, null)).isEqualTo(new PageRequest(0, 20));
  }

  @Test
  void acceptsTheBoundsOfThePageSize() {
    assertThat(PageRequest.of(0, 1).size()).isEqualTo(1);
    assertThat(PageRequest.of(0, 100).size()).isEqualTo(100);
  }

  @Test
  void refusesAPageBeforeTheFirst() {
    assertThatThrownBy(() -> PageRequest.of(-1, 20))
        .isInstanceOfSatisfying(
            InvalidCriteriaException.class,
            ex -> assertThat(ex.violations()).extracting(Violation::field).containsExactly("page"));
  }

  @Test
  void refusesASizeOutsideOneToAHundredAndReportsBothFaultsTogether() {
    assertThatThrownBy(() -> PageRequest.of(0, 0)).isInstanceOf(InvalidCriteriaException.class);
    assertThatThrownBy(() -> PageRequest.of(-1, 101))
        .isInstanceOfSatisfying(
            InvalidCriteriaException.class,
            ex ->
                assertThat(ex.violations())
                    .extracting(Violation::field)
                    .containsExactly("page", "size"));
  }

  @Test
  void computesTheOffsetWithoutOverflowing() {
    assertThat(PageRequest.of(Integer.MAX_VALUE, 100).offset()).isEqualTo(Integer.MAX_VALUE * 100L);
  }
}
