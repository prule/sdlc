package com.acme.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DomainExceptionTest {

  private static final class TestDomainException extends DomainException {
    TestDomainException(String code, String message) {
      super(code, message);
    }
  }

  @Test
  void carriesTheCodeAndTheMessage() {
    DomainException exception = new TestDomainException("SOME_CODE", "something went wrong");

    assertThat(exception.code()).isEqualTo("SOME_CODE");
    assertThat(exception.getMessage()).isEqualTo("something went wrong");
  }

  @Test
  void isARuntimeException() {
    DomainException exception = new TestDomainException("SOME_CODE", "something went wrong");

    assertThat(exception).isInstanceOf(RuntimeException.class);
  }
}
