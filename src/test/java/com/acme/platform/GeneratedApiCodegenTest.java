package com.acme.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.generated.api.HealthApi;
import com.acme.generated.api.MoviesApi;
import com.acme.generated.model.Meta;
import com.acme.generated.model.MovieCollectionEnvelope;
import com.acme.generated.model.MovieDetail;
import com.acme.generated.model.PingEnvelope;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestParam;

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
  void moviesApiGetMovieReturnsTheSharedMovieEnvelope() throws NoSuchMethodException {
    Method getMovie = MoviesApi.class.getMethod("getMovie", UUID.class);

    assertThat(getMovie.getReturnType()).isEqualTo(ResponseEntity.class);
    ParameterizedType genericReturnType = (ParameterizedType) getMovie.getGenericReturnType();
    assertThat(genericReturnType.getActualTypeArguments())
        .containsExactly(com.acme.generated.model.MovieEnvelope.class);
  }

  @Test
  void moviesApiIsAnnotatedValidated() {
    assertThat(MoviesApi.class.isAnnotationPresent(Validated.class)).isTrue();
  }

  @Test
  void moviesApiSearchMoviesHasTheExpectedSignatureAndReturnsTheSharedCollectionEnvelope()
      throws NoSuchMethodException {
    Method searchMovies =
        MoviesApi.class.getMethod(
            "searchMovies",
            String.class,
            List.class,
            Integer.class,
            Integer.class,
            BigDecimal.class,
            String.class,
            Integer.class,
            Integer.class);

    assertThat(searchMovies.getReturnType()).isEqualTo(ResponseEntity.class);
    ParameterizedType genericReturnType = (ParameterizedType) searchMovies.getGenericReturnType();
    assertThat(genericReturnType.getActualTypeArguments())
        .containsExactly(MovieCollectionEnvelope.class);
  }

  @Test
  void searchMoviesParametersCarryTheExpectedRequestParamNames() throws NoSuchMethodException {
    Method searchMovies =
        MoviesApi.class.getMethod(
            "searchMovies",
            String.class,
            List.class,
            Integer.class,
            Integer.class,
            BigDecimal.class,
            String.class,
            Integer.class,
            Integer.class);
    String[] expectedNames = {
      "title", "genre", "releaseYearFrom", "releaseYearTo", "minRating", "sort", "page", "size"
    };

    Parameter[] parameters = searchMovies.getParameters();
    for (int i = 0; i < expectedNames.length; i++) {
      RequestParam requestParam = parameters[i].getAnnotation(RequestParam.class);
      assertThat(requestParam).as("parameter %d (%s)", i, expectedNames[i]).isNotNull();
      assertThat(requestParam.value()).isEqualTo(expectedNames[i]);
    }
  }

  @Test
  void searchMoviesSortParameterIsAStringNotAnEnum() throws NoSuchMethodException {
    Method searchMovies =
        MoviesApi.class.getMethod(
            "searchMovies",
            String.class,
            List.class,
            Integer.class,
            Integer.class,
            BigDecimal.class,
            String.class,
            Integer.class,
            Integer.class);

    assertThat(searchMovies.getParameters()[5].getType()).isEqualTo(String.class);
  }

  @Test
  void searchMoviesPageSizeAndMinRatingCarryBoundsConstraints() throws NoSuchMethodException {
    Method searchMovies =
        MoviesApi.class.getMethod(
            "searchMovies",
            String.class,
            List.class,
            Integer.class,
            Integer.class,
            BigDecimal.class,
            String.class,
            Integer.class,
            Integer.class);
    Parameter[] parameters = searchMovies.getParameters();

    assertThat(hasAnnotation(parameters[4], DecimalMin.class)).isTrue();
    assertThat(hasAnnotation(parameters[4], DecimalMax.class)).isTrue();
    assertThat(hasAnnotation(parameters[6], Min.class)).isTrue();
    assertThat(hasAnnotation(parameters[7], Min.class)).isTrue();
    assertThat(hasAnnotation(parameters[7], Max.class)).isTrue();
  }

  private static boolean hasAnnotation(Parameter parameter, Class<? extends Annotation> type) {
    return parameter.getAnnotation(type) != null;
  }

  @Test
  void metaHasAnOptionalPaginationMember() throws NoSuchMethodException {
    assertThat(Meta.class.getMethod("getPagination")).isNotNull();
  }

  @Test
  void noSearchMoviesResponseModelsAreGenerated() throws IOException {
    Path modelDir = generatedModelDirectory();

    assertThat(findModelsMatching(modelDir, Pattern.compile(".*SearchMovies.*Response.*")))
        .isEmpty();
  }

  @Test
  void movieDetailGenresIsAListAndRatingIsABigDecimal() throws NoSuchMethodException {
    Method getGenres = MovieDetail.class.getMethod("getGenres");
    Method getRating = MovieDetail.class.getMethod("getRating");

    assertThat(List.class).isAssignableFrom(getGenres.getReturnType());
    assertThat(getRating.getReturnType()).isEqualTo(BigDecimal.class);
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
