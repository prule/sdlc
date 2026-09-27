package com.acme.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.generated.api.HealthApi;
import com.acme.generated.model.PingEnvelope;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/**
 * Asserts that the OpenAPI generator was fed the redocly-bundled spec (with shared component
 * schemas), not the multi-file authored spec directly. Feeding the multi-file spec directly makes
 * the generator inline and rename cross-file $ref-composed response schemas into per-operation
 * duplicates (e.g. {@code Ping200Response}) instead of binding to the shared {@code PingEnvelope}
 * component (see standards/openapi.md §5).
 */
class GeneratedApiCodegenTest {

  private static final Pattern PER_OPERATION_STATUS_RESPONSE =
      Pattern.compile(".*\\d{3}Response.*");

  @Test
  void healthApiPingReturnsTheSharedPingEnvelope() throws NoSuchMethodException {
    Method ping = HealthApi.class.getMethod("ping");

    assertThat(ping.getReturnType()).isEqualTo(ResponseEntity.class);
    ParameterizedType genericReturnType = (ParameterizedType) ping.getGenericReturnType();
    assertThat(genericReturnType.getActualTypeArguments()).containsExactly(PingEnvelope.class);
  }

  @Test
  void noPerOperationStatusResponseModelsAreGenerated() throws IOException {
    Path modelDir = generatedModelDirectory();

    assertThat(findModelsMatching(modelDir, PER_OPERATION_STATUS_RESPONSE)).isEmpty();
  }

  @Test
  void theDuplicateDetectionCheckFailsWhenAStatusResponseModelIsPresent() throws IOException {
    Path tempDir = Files.createTempDirectory("codegen-test-fixture");
    try {
      Files.createFile(tempDir.resolve("Ping200Response.java"));

      assertThat(findModelsMatching(tempDir, PER_OPERATION_STATUS_RESPONSE))
          .containsExactly("Ping200Response.java");
    } finally {
      Files.deleteIfExists(tempDir.resolve("Ping200Response.java"));
      Files.deleteIfExists(tempDir);
    }
  }

  private static Path generatedModelDirectory() {
    return Path.of(
        System.getProperty("user.dir"),
        "build",
        "generated",
        "src",
        "main",
        "java",
        "com",
        "acme",
        "generated",
        "model");
  }

  private static List<String> findModelsMatching(Path dir, Pattern pattern) throws IOException {
    try (Stream<Path> files = Files.list(dir)) {
      return files
          .map(path -> path.getFileName().toString())
          .filter(name -> pattern.matcher(name).matches())
          .toList();
    }
  }
}
