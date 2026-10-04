package com.acme.platform.web;

import jakarta.validation.constraints.Min;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * A {@code @Validated} service bean whose constrained parameter can sit at the same index as one of
 * {@link TestOnlyValidatedController}'s {@code @RequestParam}s, so a test can prove that
 * "belonging" is decided by the root bean class and method, never by index alone (design D2, task
 * 6.1).
 */
@Service
@Validated
public class TestOnlyConstrainedService {

  public void validate(@Min(0) int x) {
    // No body: the constraint is enforced by the AOP proxy before this runs.
  }
}
