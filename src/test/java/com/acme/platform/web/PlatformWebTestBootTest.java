package com.acme.platform.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Proves the {@link PlatformWebTest} slice boots and can serve a request. */
@PlatformWebTest(controllers = TestOnlyController.class)
class PlatformWebTestBootTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void bootsAndRoutesToTheTestOnlyController() throws Exception {
    mockMvc
        .perform(get("/test-only/uuid-param").param("id", "3f2b8c1e-8d4a-4c1e-9f0a-2b7d6e5c4a31"))
        .andExpect(status().isOk());
  }
}
