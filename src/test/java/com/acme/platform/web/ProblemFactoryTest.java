package com.acme.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.platform.web.ProblemFactory.InvalidParam;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

class ProblemFactoryTest {

  private final ProblemFactory problemFactory = new ProblemFactory();
  private final List<InvalidParam> sizeFault = List.of(new InvalidParam("size", "is too large"));

  @Test
  void aBadRequestListsTheOffendingParameters() {
    ProblemDetail problem = problemFactory.create(400, sizeFault);

    assertThat(problem.getProperties()).containsEntry("errors", sizeFault);
    assertThat(problem.getProperties()).containsEntry("code", "BAD_REQUEST");
  }

  @Test
  void aBadRequestWithoutNamedParametersHasNoErrors() {
    assertThat(problemFactory.create(400, List.of()).getProperties()).doesNotContainKey("errors");
    assertThat(problemFactory.create(400).getProperties()).doesNotContainKey("errors");
  }

  @Test
  void otherKindsNeverCarryErrors() {
    for (int status : new int[] {404, 405, 406, 415, 500}) {
      assertThat(problemFactory.create(status, sizeFault).getProperties())
          .as("status %d", status)
          .doesNotContainKey("errors");
    }
  }
}
