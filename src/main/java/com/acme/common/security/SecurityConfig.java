package com.acme.common.security;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless OAuth2 resource server configuration. Default-deny: every endpoint requires
 * authentication unless its path pattern is listed in {@link PublicEndpoints}, which must agree
 * with the OpenAPI operations marked {@code security: []}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final ProblemAuthenticationEntryPoint authenticationEntryPoint;
  private final ProblemAccessDeniedHandler accessDeniedHandler;

  @Value("${security.jwt.issuer-uri:}")
  private String issuerUri;

  @Value("${security.jwt.expected-audience}")
  private String expectedAudience;

  public SecurityConfig(
      ProblemAuthenticationEntryPoint authenticationEntryPoint,
      ProblemAccessDeniedHandler accessDeniedHandler) {
    this.authenticationEntryPoint = authenticationEntryPoint;
    this.accessDeniedHandler = accessDeniedHandler;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(
            headers ->
                headers.contentSecurityPolicy(
                    csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
        .authorizeHttpRequests(
            authorize -> {
              PublicEndpoints.PATTERNS.forEach(
                  pattern -> authorize.requestMatchers(pattern).permitAll());
              authorize.anyRequest().authenticated();
            })
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    // The resource server installs its own default entry point/access-denied
                    // handler for failures raised inside its filter (e.g. a malformed/invalid
                    // bearer token), which otherwise pre-empt the generic exceptionHandling(...)
                    // below and render Spring's default (non-Problem) body. Wiring them here too
                    // ensures every 401/403 from the resource server renders the shared Problem
                    // shape (standards/error-handling.md §3-4, standards/security.md §6).
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler));

    return http.build();
  }

  /**
   * Maps the {@code roles} claim to {@code ROLE_*} authorities and {@code scope} to {@code
   * SCOPE_*}.
   */
  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
    scopeConverter.setAuthorityPrefix("SCOPE_");
    scopeConverter.setAuthoritiesClaimName("scope");

    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          Collection<GrantedAuthority> authorities = new ArrayList<>(scopeConverter.convert(jwt));
          List<String> roles = jwt.getClaimAsStringList("roles");
          if (roles != null) {
            roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
          }
          return authorities;
        });
    return converter;
  }

  /**
   * Composes the issuer's default validators (signature, {@code iss}, {@code exp}, {@code nbf})
   * with an {@link AudienceValidator} enforcing {@code aud}, since issuer-uri auto-configuration
   * does not validate audience by default (standards/security.md §1). When no issuer is configured
   * (local/test environments without a real auth server), falls back to a decoder backed by a
   * locally generated key that accepts no real tokens, so the application context can start without
   * any network dependency on an external issuer.
   */
  @Bean
  public JwtDecoder jwtDecoder() {
    if (issuerUri == null || issuerUri.isBlank()) {
      return placeholderDecoder();
    }

    NimbusJwtDecoder jwtDecoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuerUri);

    OAuth2TokenValidator<Jwt> defaultValidator = JwtValidators.createDefaultWithIssuer(issuerUri);
    OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(expectedAudience);
    jwtDecoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(defaultValidator, audienceValidator));

    return jwtDecoder;
  }

  private static JwtDecoder placeholderDecoder() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair keyPair = generator.generateKeyPair();
      return NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Unable to generate a placeholder JWT decoder key", e);
    }
  }
}
