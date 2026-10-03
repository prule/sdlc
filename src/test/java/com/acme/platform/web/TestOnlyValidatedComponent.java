package com.acme.platform.web;

import jakarta.validation.constraints.Max;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Test-only {@code @Validated} bean that is not a controller. A constraint violation it raises is a
 * server-side validation fault, which must stay {@code 500}, never a named {@code 400} (design D3,
 * uniform-responses "Refused parameter is named, never echoed").
 */
@Component
@Validated
public class TestOnlyValidatedComponent {

  public int accept(@Max(1) int value) {
    return value;
  }
}
