package com.acme.shared.domain.paging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** JDK-only unit tests for {@link PageRequest} (design D3, task 3.1). */
class PageRequestTest {

  @Test
  void rejectsANegativePage() {
    assertThatThrownBy(() -> new PageRequest(-1, 20)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsASizeOfZero() {
    assertThatThrownBy(() -> new PageRequest(0, 0)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsASizeAboveTheMaximum() {
    assertThatThrownBy(() -> new PageRequest(0, 101)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsPageZero() {
    assertThat(new PageRequest(0, 20).page()).isEqualTo(0);
  }

  @Test
  void acceptsASizeOfOne() {
    assertThat(new PageRequest(0, 1).size()).isEqualTo(1);
  }

  @Test
  void acceptsTheMaximumSize() {
    assertThat(new PageRequest(0, 100).size()).isEqualTo(100);
  }

  @Test
  void offsetIsALongComputedWithoutOverflow() {
    PageRequest request = new PageRequest(2147483647, 100);

    assertThat(request.offset()).isEqualTo(2147483647L * 100L);
  }

  @Test
  void defaultAndMaxSizeConstants() {
    assertThat(PageRequest.DEFAULT_SIZE).isEqualTo(20);
    assertThat(PageRequest.MAX_SIZE).isEqualTo(100);
  }
}
