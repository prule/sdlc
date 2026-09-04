package com.acme.common.security;

import java.util.List;

/**
 * Single source of truth for which path patterns are public (no authentication required). {@link
 * SecurityConfig} permits exactly these patterns; a consistency test asserts they agree with the
 * OpenAPI operations marked {@code security: []}, so the two sources of truth cannot drift.
 */
public final class PublicEndpoints {

  /**
   * Path patterns (Spring MVC pattern syntax), relative to the servlet context path ({@code
   * server.servlet.context-path: /api/v1}), that require no authentication. Spring Security matches
   * request matchers against the path within the application, i.e. excluding the context path.
   */
  public static final List<String> PATTERNS =
      List.of("/ping", "/samples", "/movies", "/movies/{id}", "/movies/{id}/credits");

  private PublicEndpoints() {}
}
