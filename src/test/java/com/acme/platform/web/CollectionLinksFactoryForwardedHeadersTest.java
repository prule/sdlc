package com.acme.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Web-slice coverage for {@link CollectionLinksFactory} honouring forwarded scheme/host (design D6,
 * task 6.2), through a real MockMvc round trip so {@code ForwardedHeaderFilter} actually runs.
 */
@PlatformWebTest(controllers = TestOnlyCollectionLinksController.class)
@Import(CollectionLinksFactory.class)
class CollectionLinksFactoryForwardedHeadersTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void everyHrefHonoursForwardedProtoAndHost() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/test-only/collection-links/movies")
                    .header("X-Forwarded-Proto", "https")
                    .header("X-Forwarded-Host", "api.example.test"))
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    for (String relation : new String[] {"self", "first", "last"}) {
      assertThat(body.get(relation).get("href").asText())
          .startsWith("https://api.example.test/test-only/collection-links/movies");
    }
  }
}
