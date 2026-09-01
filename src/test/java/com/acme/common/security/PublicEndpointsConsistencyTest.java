package com.acme.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileReader;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Asserts that {@link PublicEndpoints} (the security-config source of truth) and the OpenAPI
 * operations marked {@code security: []} (the contract source of truth) agree, so the two cannot
 * silently drift.
 */
class PublicEndpointsConsistencyTest {

  private static final File OPENAPI_DIR = new File("src/main/resources/openapi").getAbsoluteFile();

  @Test
  @SuppressWarnings("unchecked")
  void publicEndpointsAgreeWithOpenApiSecurityLessOperations() throws Exception {
    Yaml yaml = new Yaml();
    Map<String, Object> root;
    try (FileReader reader = new FileReader(new File(OPENAPI_DIR, "openapi.yaml"))) {
      root = yaml.load(reader);
    }

    Map<String, Object> paths = (Map<String, Object>) root.get("paths");
    Set<String> publicOperationPaths = new LinkedHashSet<>();

    for (Map.Entry<String, Object> entry : paths.entrySet()) {
      String pathKey = entry.getKey();
      Map<String, Object> pathItem = resolvePathItem((Map<String, Object>) entry.getValue(), yaml);

      for (Map.Entry<String, Object> methodEntry : pathItem.entrySet()) {
        if (!(methodEntry.getValue() instanceof Map)) {
          continue;
        }
        Map<String, Object> operation = (Map<String, Object>) methodEntry.getValue();
        Object security = operation.get("security");
        if (security instanceof List<?> securityList && securityList.isEmpty()) {
          publicOperationPaths.add(pathKey);
        }
      }
    }

    assertThat(publicOperationPaths)
        .as("OpenAPI operations marked security: []")
        .containsExactlyInAnyOrderElementsOf(PublicEndpoints.PATTERNS);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> resolvePathItem(Map<String, Object> pathValue, Yaml yaml)
      throws Exception {
    if (pathValue.containsKey("$ref")) {
      String ref = (String) pathValue.get("$ref");
      File refFile = new File(OPENAPI_DIR, ref);
      try (FileReader reader = new FileReader(refFile)) {
        return yaml.load(reader);
      }
    }
    return pathValue;
  }
}
