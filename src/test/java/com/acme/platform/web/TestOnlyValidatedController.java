package com.acme.platform.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implements {@link TestOnlyValidatedApi}, so tests exercise the {@code
 * MethodValidationPostProcessor} path exactly as the generated {@code MoviesApi} does (design D2,
 * task 6.1).
 */
@RestController
public class TestOnlyValidatedController implements TestOnlyValidatedApi {

  private final TestOnlyConstrainedService constrainedService;

  public TestOnlyValidatedController(TestOnlyConstrainedService constrainedService) {
    this.constrainedService = constrainedService;
  }

  @Override
  public ResponseEntity<String> bounds(Integer p, Integer s) {
    return ResponseEntity.ok("ok");
  }

  @Override
  public ResponseEntity<String> nameAttribute(Integer o) {
    return ResponseEntity.ok("ok");
  }

  @Override
  public ResponseEntity<String> pathVariable(Integer id) {
    return ResponseEntity.ok("ok");
  }

  @Override
  public ResponseEntity<String> serviceConflict(Integer p, Integer s) {
    // Deliberately violates the service's own constraint, at the same index (0) as this
    // method's first @RequestParam, to prove that coincidence alone does not make it "belong"
    // to this handler (design D2).
    constrainedService.validate(-1);
    return ResponseEntity.ok("ok");
  }
}
