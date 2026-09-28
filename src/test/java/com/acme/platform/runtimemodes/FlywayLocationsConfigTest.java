package com.acme.platform.runtimemodes;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Plain-JUnit check (no Spring context) that the standalone-only demo seed (design D7) is wired
 * through Flyway locations, not through a Postgres-booting test: {@code application-postgres.yml}
 * excludes {@code db/demo} and the default {@code application.yml} includes it.
 */
class FlywayLocationsConfigTest {

  private static final YamlPropertySourceLoader LOADER = new YamlPropertySourceLoader();

  @Test
  void postgresProfileExcludesTheDemoSeed() throws IOException {
    String locations = loadFlywayLocations("application-postgres.yml");

    assertThat(locations).isEqualTo("classpath:db/migration");
  }

  @Test
  void defaultProfileIncludesTheDemoSeed() throws IOException {
    String locations = loadFlywayLocations("application.yml");

    assertThat(locations).contains("classpath:db/demo");
  }

  private String loadFlywayLocations(String fileName) throws IOException {
    List<PropertySource<?>> sources = LOADER.load(fileName, new ClassPathResource(fileName));
    for (PropertySource<?> source : sources) {
      Object value = source.getProperty("spring.flyway.locations");
      if (value != null) {
        return value.toString();
      }
    }
    throw new AssertionError("spring.flyway.locations not found in " + fileName);
  }
}
