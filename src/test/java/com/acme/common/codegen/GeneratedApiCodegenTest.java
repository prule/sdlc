package com.acme.common.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.generated.api.HealthApi;
import com.acme.generated.model.PingEnvelope;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/**
 * Regression guard for the bundle-then-generate OpenAPI codegen pipeline (see standards/openapi.md
 * §5). Fails the build if the generator ever stops binding operations to the shared component
 * schemas (e.g. someone reverts {@code openApiGenerate} to consume the multi-file spec directly
 * instead of the {@code redocly bundle} output), which would resurrect per-operation {@code
 * <Operation><Status>Response*} duplicate DTOs.
 */
class GeneratedApiCodegenTest {

  /** Matches the per-operation duplicate DTO family the pipeline must never re-emit. */
  private static final Pattern PER_OPERATION_RESPONSE_DUPLICATE =
      Pattern.compile("^[A-Za-z]+[0-9]{3}Response.*$");

  private static final Path GENERATED_MODEL_DIR =
      Paths.get("build/generated/src/main/java/com/acme/generated/model");

  @Test
  void healthApiPingReturnsTheSharedPingEnvelopeType() throws NoSuchMethodException {
    Method ping = HealthApi.class.getMethod("ping", java.util.UUID.class);

    assertThat(ping.getReturnType()).isEqualTo(ResponseEntity.class);

    Type genericReturnType = ping.getGenericReturnType();
    assertThat(genericReturnType).isInstanceOf(ParameterizedType.class);
    ParameterizedType parameterizedReturnType = (ParameterizedType) genericReturnType;
    assertThat(parameterizedReturnType.getActualTypeArguments())
        .containsExactly(PingEnvelope.class);
  }

  @Test
  void noGeneratedModelIsAPerOperationResponseDuplicate() throws IOException {
    List<String> modelClassNames = generatedModelClassNames();

    List<String> duplicates =
        modelClassNames.stream()
            .filter(name -> PER_OPERATION_RESPONSE_DUPLICATE.matcher(name).matches())
            .collect(Collectors.toList());

    assertThat(duplicates).as("per-operation response duplicate models").isEmpty();
  }

  @Test
  void exactlyOneSharedProblemModelIsGenerated() throws IOException {
    List<String> modelClassNames = generatedModelClassNames();

    long problemModelCount =
        modelClassNames.stream().filter(name -> name.equals("Problem")).count();

    assertThat(problemModelCount).as("generated Problem model count").isEqualTo(1);
  }

  private static List<String> generatedModelClassNames() throws IOException {
    assertThat(GENERATED_MODEL_DIR)
        .as("generated model directory (run ./gradlew openApiGenerate first)")
        .exists();

    try (Stream<Path> files = Files.list(GENERATED_MODEL_DIR)) {
      return files
          .filter(path -> path.toString().endsWith(".java"))
          .map(path -> path.getFileName().toString().replace(".java", ""))
          .collect(Collectors.toList());
    }
  }
}
