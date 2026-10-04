package com.acme.platform.web;

import java.beans.PropertyEditorSupport;
import java.util.UUID;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * Registers {@link StrictUuidConverter} for every UUID-typed path variable or request parameter
 * across the service (design D4). Registering it as a plain {@code Converter} via {@code
 * FormatterRegistry} is not enough: Spring's data binder falls back to the JDK's lenient {@code
 * UUID.fromString} (via the built-in {@code UUIDEditor}) whenever a conversion-service converter
 * throws, silently accepting non-canonical forms such as {@code 1-1-1-1-1} again. Registering a
 * custom {@link java.beans.PropertyEditor} for {@code UUID.class} instead takes priority over that
 * default editor and is not retried on failure.
 */
@ControllerAdvice
public class StrictUuidWebConfig {

  private final StrictUuidConverter strictUuidConverter;

  public StrictUuidWebConfig(StrictUuidConverter strictUuidConverter) {
    this.strictUuidConverter = strictUuidConverter;
  }

  @InitBinder
  public void registerStrictUuidEditor(WebDataBinder binder) {
    binder.registerCustomEditor(UUID.class, new StrictUuidPropertyEditor());
  }

  private final class StrictUuidPropertyEditor extends PropertyEditorSupport {
    @Override
    public void setAsText(String text) {
      setValue(strictUuidConverter.convert(text));
    }
  }
}
