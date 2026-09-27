package com.acme.platform.runtimemodes;

import com.acme.testsupport.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Persistent mode (the {@code postgres} profile, against a real Testcontainers PostgreSQL instance)
 * serves the same UC-000 behaviours as standalone mode (platform/runtime-modes, design D8).
 */
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
class PersistentModeIntegrationTest extends PostgresIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void persistentModeServesEveryUc000Behaviour() throws Exception {
    Uc000Assertions.runAll(mockMvc);
  }
}
