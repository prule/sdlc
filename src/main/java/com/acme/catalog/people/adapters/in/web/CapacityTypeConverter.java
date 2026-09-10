package com.acme.catalog.people.adapters.in.web;

import com.acme.generated.model.CapacityType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Converts the {@code capacity} query parameter's raw string (the OpenAPI enum's declared values —
 * {@code acting}/{@code non-acting}) to the generated {@link CapacityType} enum. Required because
 * Spring MVC's default {@code String}-to-enum conversion calls {@link Enum#valueOf}, which expects
 * the Java constant name ({@code ACTING}/{@code NON_ACTING}), not the OpenAPI value; without this
 * converter a well-formed {@code capacity=acting} request would itself fail binding. An
 * unrecognised value still fails (via {@link CapacityType#fromValue}'s {@link
 * IllegalArgumentException}), producing the same {@code 400} as any other enum bind failure (flow
 * 3d).
 */
@Component
public class CapacityTypeConverter implements Converter<String, CapacityType> {

  @Override
  public CapacityType convert(@NonNull String source) {
    return CapacityType.fromValue(source);
  }
}
