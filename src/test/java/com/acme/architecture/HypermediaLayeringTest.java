package com.acme.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards the "link assembly is a web-adapter concern only" rule (see {@code
 * openspec/changes/adopt-hal-hypermedia}, D7 / requirement "Link assembly is a web-adapter concern
 * only"): no class under a {@code domain} or {@code application} package may import Spring HATEOAS.
 * Source-scan based (rather than reflection over compiled classes) since import statements are not
 * retained in bytecode.
 */
class HypermediaLayeringTest {

  private static final Path MAIN_JAVA = Paths.get("src/main/java");
  private static final String FORBIDDEN_IMPORT = "org.springframework.hateoas";

  @Test
  void domainAndApplicationPackagesDoNotImportSpringHateoas() throws IOException {
    assertThat(MAIN_JAVA).exists();

    try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
      List<Path> violations =
          files
              .filter(path -> path.toString().endsWith(".java"))
              .filter(HypermediaLayeringTest::isDomainOrApplicationClass)
              .filter(HypermediaLayeringTest::importsSpringHateoas)
              .toList();

      assertThat(violations).as("domain/application classes importing Spring HATEOAS").isEmpty();
    }
  }

  private static boolean isDomainOrApplicationClass(Path path) {
    String normalized = path.toString().replace('\\', '/');
    return normalized.contains("/domain/") || normalized.contains("/application/");
  }

  private static boolean importsSpringHateoas(Path path) {
    try {
      return Files.readString(path).contains(FORBIDDEN_IMPORT);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
