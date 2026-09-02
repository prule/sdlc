package com.acme.conformance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.common.error.GlobalExceptionHandler;
import com.acme.common.security.ProblemAccessDeniedHandler;
import com.acme.common.security.ProblemAuthenticationEntryPoint;
import com.acme.common.security.SecurityConfig;
import com.acme.platform.health.adapters.in.web.PingController;
import com.acme.platform.health.application.port.in.PingUseCase;
import com.acme.platform.health.domain.model.PingStatus;
import com.acme.platform.sample.adapters.in.web.SampleController;
import com.acme.platform.sample.application.port.in.ListSamplesUseCase;
import com.acme.platform.sample.domain.model.SampleItem;
import com.acme.platform.sample.domain.model.SamplePage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileReader;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.Yaml;

/**
 * Conformance test (spec {@code platform/hypermedia-links}, "Runtime output conforms to the
 * documented relations"): parses the bundled OpenAPI spec to extract each proof operation's
 * documented {@code _links} relation set, hits the runtime endpoint, and asserts every relation key
 * present in the runtime {@code data._links} is declared in that operation's documented {@code
 * _links} schema.
 */
@WebMvcTest(controllers = {PingController.class, SampleController.class})
@Import({
  SecurityConfig.class,
  ProblemAuthenticationEntryPoint.class,
  ProblemAccessDeniedHandler.class,
  GlobalExceptionHandler.class
})
class HalLinksConformanceTest {

  private static final File BUNDLED_SPEC =
      new File("build/openapi/openapi.bundled.yaml").getAbsoluteFile();

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PingUseCase pingUseCase;
  @MockitoBean private ListSamplesUseCase listSamplesUseCase;

  private static SampleItem item(int i) {
    return new SampleItem(UUID.nameUUIDFromBytes(("sample-" + i).getBytes()), "Sample item " + i);
  }

  @Test
  void pingSelfLinkRelationsAreAllDocumented() throws Exception {
    given(pingUseCase.ping()).willReturn(PingStatus.ok(Instant.now()));

    Set<String> documented = documentedLinkRelations("PingEnvelope");

    var result = mockMvc.perform(get("/ping")).andExpect(status().isOk()).andReturn();
    Set<String> runtime = runtimeLinkRelations(result.getResponse().getContentAsString());

    assertThat(documented).as("documented relations for GET /ping").isNotEmpty();
    assertThat(runtime).as("runtime relations for GET /ping").isSubsetOf(documented);
  }

  @Test
  void sampleCollectionLinkRelationsAreAllDocumented() throws Exception {
    given(listSamplesUseCase.listSamples(1, 5))
        .willReturn(
            new SamplePage(List.of(item(6), item(7), item(8), item(9), item(10)), 1, 5, 25, 5));

    Set<String> documented = documentedLinkRelations("SampleCollectionEnvelope");

    var result =
        mockMvc
            .perform(get("/samples").param("page", "1").param("size", "5"))
            .andExpect(status().isOk())
            .andReturn();
    Set<String> runtime = runtimeLinkRelations(result.getResponse().getContentAsString());

    // Exercise a middle page so self/first/last/prev/next are all emitted at once.
    assertThat(runtime).containsExactlyInAnyOrder("self", "first", "last", "prev", "next");
    assertThat(runtime).as("runtime relations for GET /samples").isSubsetOf(documented);
  }

  @SuppressWarnings("unchecked")
  private static Set<String> documentedLinkRelations(String envelopeSchemaName) throws Exception {
    Map<String, Object> root;
    try (FileReader reader = new FileReader(BUNDLED_SPEC)) {
      root = new Yaml().load(reader);
    }
    Map<String, Object> schemas =
        (Map<String, Object>) ((Map<String, Object>) root.get("components")).get("schemas");

    Map<String, Object> envelope = (Map<String, Object>) schemas.get(envelopeSchemaName);
    Map<String, Object> envelopeProps = (Map<String, Object>) envelope.get("properties");
    Map<String, Object> dataSchema =
        resolveRef((Map<String, Object>) envelopeProps.get("data"), schemas);

    Map<String, Object> dataProps = (Map<String, Object>) dataSchema.get("properties");
    Map<String, Object> linksSchema =
        resolveRef((Map<String, Object>) dataProps.get("_links"), schemas);
    Map<String, Object> linksProps = (Map<String, Object>) linksSchema.get("properties");

    return linksProps.keySet();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> resolveRef(
      Map<String, Object> schemaOrRef, Map<String, Object> schemas) {
    if (schemaOrRef.containsKey("$ref")) {
      String ref = (String) schemaOrRef.get("$ref");
      String schemaName = ref.substring(ref.lastIndexOf('/') + 1);
      return (Map<String, Object>) schemas.get(schemaName);
    }
    return schemaOrRef;
  }

  private static Set<String> runtimeLinkRelations(String body) throws Exception {
    JsonNode links = new ObjectMapper().readTree(body).path("data").path("_links");
    return java.util.stream.StreamSupport.stream(
            java.util.Spliterators.spliteratorUnknownSize(links.fieldNames(), 0), false)
        .collect(java.util.stream.Collectors.toSet());
  }
}
