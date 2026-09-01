package com.acme.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;

/**
 * Confirms the same validator composition used by {@link SecurityConfig#jwtDecoder()} (issuer
 * defaults + {@link AudienceValidator}, combined via {@link DelegatingOAuth2TokenValidator})
 * actually rejects a token whose {@code aud} does not match the configured audience, even though
 * the token is otherwise well formed for the configured issuer.
 */
class ComposedJwtValidatorTest {

  private static final String ISSUER = "https://issuer.example.com";
  private static final String EXPECTED_AUDIENCE = "sdlc-api";

  private final OAuth2TokenValidator<Jwt> composedValidator =
      new DelegatingOAuth2TokenValidator<>(
          JwtValidators.createDefaultWithIssuer(ISSUER), new AudienceValidator(EXPECTED_AUDIENCE));

  @Test
  void composedValidator_acceptsATokenWithTheCorrectIssuerAndAudience() {
    Jwt token = jwt(ISSUER, List.of(EXPECTED_AUDIENCE));

    OAuth2TokenValidatorResult result = composedValidator.validate(token);

    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  void composedValidator_rejectsATokenWithTheCorrectIssuerButWrongAudience() {
    Jwt token = jwt(ISSUER, List.of("some-other-api"));

    OAuth2TokenValidatorResult result = composedValidator.validate(token);

    assertThat(result.hasErrors()).isTrue();
  }

  private static Jwt jwt(String issuer, List<String> audience) {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .claim("iss", issuer)
        .claim("aud", audience)
        .claim("sub", "user-1")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60))
        .build();
  }
}
