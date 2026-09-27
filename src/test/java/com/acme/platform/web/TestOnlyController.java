package com.acme.platform.web;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
 * body media type the operation does not accept (415), and framework-resolved statuses outside the
 * closed kind set (409, 503) that must collapse to 400/500. Exists solely so {@code platform}
 * web-slice tests can assert the uniform failure form without inventing a real capability (design
 * D3, task 6.0).
 */
@RestController
@RequestMapping("/test-only")
public class TestOnlyController {

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
}
