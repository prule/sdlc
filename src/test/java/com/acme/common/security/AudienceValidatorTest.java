package com.acme.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class AudienceValidatorTest {

  private static final String EXPECTED_AUDIENCE = "sdlc-api";

  private final AudienceValidator validator = new AudienceValidator(EXPECTED_AUDIENCE);

  @Test
  void validate_succeedsWhenTheAudienceClaimContainsTheExpectedAudience() {
    Jwt token = jwtWithAudience(List.of(EXPECTED_AUDIENCE));

    OAuth2TokenValidatorResult result = validator.validate(token);

    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  void validate_failsWhenTheAudienceClaimDoesNotContainTheExpectedAudience() {
    Jwt token = jwtWithAudience(List.of("some-other-api"));

    OAuth2TokenValidatorResult result = validator.validate(token);

    assertThat(result.hasErrors()).isTrue();
  }

  @Test
  void validate_failsWhenTheAudienceClaimIsMissing() {
    Jwt token = jwtWithoutAudience();

    OAuth2TokenValidatorResult result = validator.validate(token);

    assertThat(result.hasErrors()).isTrue();
  }

  private static Jwt jwtWithAudience(List<String> audience) {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .claim("aud", audience)
        .claim("sub", "user-1")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60))
        .build();
  }

  private static Jwt jwtWithoutAudience() {
    return new Jwt(
        "token",
        Instant.now(),
        Instant.now().plusSeconds(60),
        Map.of("alg", "RS256"),
        Map.of("sub", "user-1"));
  }
}
