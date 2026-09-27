package com.acme.platform.runtimemodes;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The single H2-booting test {@code standards/testing.md} permits: it proves the H2 default runtime
 * configuration (no profile active) works, not persistence logic. Standalone mode needs no external
 * infrastructure (platform/runtime-modes).
 */
@SpringBootTest
@AutoConfigureMockMvc
class H2DefaultRuntimeSmokeTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void standaloneModeServesEveryUc000Behaviour() throws Exception {
    Uc000Assertions.runAll(mockMvc);
  }
}
