package com.acme.platform.web;

import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Converts a path/request-parameter value to a {@link UUID}, accepting only the canonical
 * 36-character form (8-4-4-4-12 hexadecimal digits separated by hyphens, any letter case). {@link
 * UUID#fromString} alone would silently accept non-canonical forms such as {@code 1-1-1-1-1}
 * (design D4); registering this converter closes that gap for every UUID-typed parameter.
 */
@Component
public class StrictUuidConverter implements Converter<String, UUID> {

  private static final Pattern CANONICAL_UUID =
      Pattern.compile("^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$");

  @Override
  public UUID convert(String source) {
    if (!CANONICAL_UUID.matcher(source).matches()) {
      throw new IllegalArgumentException("not a canonical UUID");
    }
    return UUID.fromString(source);
  }
}
