package com.acme.shared.domain.paging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** JDK-only unit tests for {@link Page} (design D3, task 3.1). */
class PageTest {

  @ParameterizedTest
  @CsvSource({"0,20,0", "20,20,1", "25,20,2", "25,7,4"})
  void totalPagesRoundsUp(long totalElements, int size, int expectedTotalPages) {
    Page<String> page = new Page<>(List.of(), new PageRequest(0, size), totalElements);

    assertThat(page.totalPages()).isEqualTo(expectedTotalPages);
  }
}
