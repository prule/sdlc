package com.acme.common.security;

import java.util.List;

/**
 * Path patterns for the interactive API documentation tooling: Swagger UI (served from the local
 * {@code org.webjars:swagger-ui} webjar, no springdoc) and the served authored bundled spec.
 * Deliberately SEPARATE from {@link PublicEndpoints#PATTERNS}: docs paths are
 * platform/representation-tooling, not part of the HTTP contract, so they must never be added to
 * the contract's public-endpoints source of truth (that would break {@code
 * PublicEndpointsConsistencyTest}, which compares {@code PublicEndpoints.PATTERNS} exactly against
 * the OpenAPI operations marked {@code security: []}).
 *
 * <p>{@link SecurityConfig} permits exactly these patterns via a second, higher-precedence {@code
 * SecurityFilterChain} scoped to them, so the docs surface can be made public and CSP-relaxed
 * without touching the contract's public surface or the strict global CSP.
 */
public final class DocsUiEndpoints {

  /**
   * Path patterns (Spring MVC pattern syntax), relative to the servlet context path ({@code
   * server.servlet.context-path: /api/v1}), that serve the app-owned interactive API documentation
   * UI, the local {@code org.webjars:swagger-ui} webjar assets it loads, and the authored bundled
   * spec it renders. There is no springdoc dependency, so no annotation-generated spec endpoint
   * exists to permit.
   */
  public static final List<String> PATTERNS =
      List.of("/swagger-ui/**", "/webjars/**", "/openapi/**");

  private DocsUiEndpoints() {}
}
