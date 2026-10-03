package com.acme.platform.web;

import com.acme.shared.domain.InvalidRequestException;
import com.acme.shared.domain.ResourceNotFoundException;
import jakarta.validation.constraints.Max;
import java.util.UUID;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Test-only endpoints that exercise failure kinds no real UC-000 operation triggers: an unexpected
 * exception (500), a required parameter of a type that can be missing or malformed (400), a request
 * body media type the operation does not accept (415), framework-resolved statuses outside the
 * closed kind set (409, 503) that must collapse to 400/500, and a domain-level {@link
 * ResourceNotFoundException} (404). Exists solely so {@code platform} web-slice tests can assert
 * the uniform failure form without inventing a real capability (design D3, task 6.0).
 *
 * <p>Class-annotated {@code @Validated}, like {@code MovieController}, so bounded parameters are
 * checked before the handler runs. Also exercises the named-parameter {@code 400}: bounded
 * parameters whose published names differ from their Java names, an {@link
 * InvalidRequestException}, and a constraint violation raised by a non-controller bean, which must
 * stay {@code 500} (add-movie-search design D3, task 6.1).
 */
@RestController
@Validated
@RequestMapping("/test-only")
@Import(TestOnlyValidatedComponent.class)
public class TestOnlyController {

  private final TestOnlyValidatedComponent validatedComponent;

  public TestOnlyController(TestOnlyValidatedComponent validatedComponent) {
    this.validatedComponent = validatedComponent;
  }

  @GetMapping("/throws")
  public String throwsUnexpectedly() {
    throw new IllegalStateException("secret-db-host:5432 refused");
  }

  @GetMapping("/uuid-param")
  public String requiresUuidParam(@RequestParam("id") UUID id) {
    return id.toString();
  }

  @PostMapping(path = "/consumes-json", consumes = MediaType.APPLICATION_JSON_VALUE)
  public String consumesJson(@RequestBody String body) {
    return body;
  }

  @GetMapping("/conflict")
  public String conflicts() {
    throw new ResponseStatusException(HttpStatus.CONFLICT, "row 42 locked by tx 7");
  }

  @GetMapping("/unavailable")
  public String unavailable() {
    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "pool exhausted");
  }

  @GetMapping("/not-found")
  public String notFound() {
    throw new ResourceNotFoundException("no resource with id secret-internal-id-42");
  }

  @GetMapping("/bounded")
  public String bounded(
      @RequestParam("first-param") @Max(10) int a, @RequestParam("second-param") @Max(10) int b) {
    return a + "," + b;
  }

  @GetMapping("/invalid-request")
  public String invalidRequest() {
    throw new InvalidRequestException("thing");
  }

  @GetMapping("/non-controller-violation")
  public String nonControllerViolation() {
    return String.valueOf(validatedComponent.accept(2));
  }
}
