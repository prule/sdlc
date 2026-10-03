package com.acme.catalog.movies.adapters.in.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/** Reads {@code searchMovies}' query parameter names from the bundled interface description. */
final class SearchOperationParameters {

  private SearchOperationParameters() {}

  @SuppressWarnings("unchecked")
  static List<String> fromBundledSpec() throws IOException {
    Path bundled =
        Path.of(System.getProperty("user.dir"), "build", "openapi", "openapi.bundled.yaml");
    try (InputStream in = Files.newInputStream(bundled)) {
      Map<String, Object> spec = new Yaml().load(in);
      Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
      Map<String, Object> get =
          (Map<String, Object>) ((Map<String, Object>) paths.get("/movies")).get("get");
      Map<String, Object> parameters =
          (Map<String, Object>)
              ((Map<String, Object>) spec.get("components")).getOrDefault("parameters", Map.of());
      return ((List<Map<String, Object>>) get.get("parameters"))
          .stream()
              .map(
                  parameter -> {
                    Object ref = parameter.get("$ref");
                    return ref == null
                        ? parameter
                        : (Map<String, Object>)
                            parameters.get(
                                ref.toString().substring(ref.toString().lastIndexOf('/') + 1));
                  })
              .map(parameter -> parameter.get("name").toString())
              .toList();
    }
  }
}
