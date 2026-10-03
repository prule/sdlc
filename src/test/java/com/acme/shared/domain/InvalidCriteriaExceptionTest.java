package com.acme.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.shared.domain.InvalidCriteriaException.Violation;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvalidCriteriaExceptionTest {

  @Test
  void isADomainExceptionWithTheBadRequestCodeAndItsViolations() {
    InvalidCriteriaException exception =
        new InvalidCriteriaException(
            List.of(
                new Violation("releaseYearFrom", "must not be after releaseYearTo"),
                new Violation("releaseYearTo", "must not be before releaseYearFrom")));

    assertThat(exception).isInstanceOf(DomainException.class);
    assertThat(exception.code()).isEqualTo("BAD_REQUEST");
    assertThat(exception.violations())
        .extracting(Violation::field)
        .containsExactly("releaseYearFrom", "releaseYearTo");
    assertThat(exception.getMessage()).contains("releaseYearFrom", "releaseYearTo");
  }

  @Test
  void requiresAtLeastOneViolation() {
    assertThatThrownBy(() -> new InvalidCriteriaException(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
