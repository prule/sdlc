package com.acme.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link StrictUuidConverter} accepts only the canonical 36-character UUID form (design D4); every
 * other value must be rejected before any lookup, rather than silently normalised the way {@link
 * UUID#fromString} would.
 */
class StrictUuidConverterTest {

  private final StrictUuidConverter converter = new StrictUuidConverter();

  @ParameterizedTest
  @ValueSource(
      strings = {
        "6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b",
        "6F1C2A3B-4D5E-4F60-8A7B-9C0D1E2F3A4B",
        "6f1C2a3B-4D5e-4f60-8A7b-9c0D1e2F3a4B"
      })
  void acceptsCanonicalFormInAnyLetterCase(String value) {
    assertThat(converter.convert(value)).isEqualTo(UUID.fromString(value.toLowerCase()));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "1-1-1-1-1",
        "6f1c2a3b4d5e4f608a7b9c0d1e2f3a4b",
        "123",
        "",
        "6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4bX",
        " 6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b",
        "6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b ",
      })
  void rejectsAnythingOtherThanTheCanonicalForm(String value) {
    assertThatThrownBy(() -> converter.convert(value)).isInstanceOf(IllegalArgumentException.class);
  }
}
