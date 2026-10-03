package com.acme.platform.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * A test-only interface annotated {@code @Validated}, mirroring the generated {@code MoviesApi}
 * shape (design D2, task 6.1): the parameters carry {@code @Min}/{@code @Max} directly and are
 * bound by {@code @RequestParam}, with Java argument names ({@code p}/{@code s}) deliberately
 * different from the {@code @RequestParam} names ({@code page}/{@code size}), so a test asserting
 * the named parameter proves the name comes from the annotation, never the argument name.
 */
@Validated
public interface TestOnlyValidatedApi {

  @GetMapping("/test-only/validated/bounds")
  ResponseEntity<String> bounds(
      @Min(0) @RequestParam("page") Integer p, @Max(100) @RequestParam("size") Integer s);

  @GetMapping("/test-only/validated/name-attribute")
  ResponseEntity<String> nameAttribute(@Min(0) @RequestParam(name = "offset") Integer o);

  @GetMapping("/test-only/validated/path/{id}")
  ResponseEntity<String> pathVariable(@Min(0) @PathVariable("id") Integer id);

  @GetMapping("/test-only/validated/service-conflict")
  ResponseEntity<String> serviceConflict(
      @RequestParam("page") Integer p, @RequestParam("size") Integer s);

  /**
   * The result violates its declared constraint (the implementation returns {@code null}), so the
   * method-validation proxy raises a real return-value violation (design D3): a server-side fault.
   */
  @GetMapping("/test-only/validated/result-constraint")
  @NotNull(message = "secret-detail must not be blank")
  ResponseEntity<String> resultConstraint();

  /** Each value of a repeated query parameter is constrained: a violation is client input. */
  @GetMapping("/test-only/validated/elements")
  ResponseEntity<String> elements(@RequestParam("ids") List<@Min(0) Integer> ids);

  /**
   * Both a constrained {@code page} parameter and a constrained result, used only by the
   * handler-level mixed-violation test (design D3).
   */
  @GetMapping("/test-only/validated/mixed")
  @NotNull
  ResponseEntity<String> mixed(@Min(0) @RequestParam("page") Integer p);
}
