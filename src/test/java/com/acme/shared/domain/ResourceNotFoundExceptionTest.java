package com.acme.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResourceNotFoundExceptionTest {

  @Test
  void isADomainExceptionWithTheNotFoundCode() {
    ResourceNotFoundException exception = new ResourceNotFoundException("no such movie");

    assertThat(exception).isInstanceOf(DomainException.class);
    assertThat(exception.code()).isEqualTo("NOT_FOUND");
    assertThat(exception.getMessage()).isEqualTo("no such movie");
  }
}
