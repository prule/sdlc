package com.acme.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InvalidRequestExceptionTest {

  @Test
  void isADomainExceptionWithTheBadRequestCodeAndTheField() {
    InvalidRequestException exception = new InvalidRequestException("sort");

    assertThat(exception).isInstanceOf(DomainException.class);
    assertThat(exception.code()).isEqualTo("BAD_REQUEST");
    assertThat(exception.field()).isEqualTo("sort");
  }

  @Test
  void refusesANullField() {
    assertThatThrownBy(() -> new InvalidRequestException(null))
        .isInstanceOf(NullPointerException.class);
  }
}
