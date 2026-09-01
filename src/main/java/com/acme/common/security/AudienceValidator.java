package com.acme.common.security;

import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Enforces that the token's {@code aud} claim contains the expected audience for this service. The
 * default issuer-uri auto-configuration does not validate {@code aud}; per standards/security.md §1
 * it must be validated on every request.
 */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

  private static final OAuth2Error INVALID_AUDIENCE =
      new OAuth2Error("invalid_token", "The required audience is missing", null);

  private final String expectedAudience;

  public AudienceValidator(String expectedAudience) {
    this.expectedAudience = expectedAudience;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt token) {
    List<String> audiences = token.getAudience();
    if (audiences != null && audiences.contains(expectedAudience)) {
      return OAuth2TokenValidatorResult.success();
    }
    return OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
  }
}
