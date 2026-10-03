package com.acme.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.generated.api.HealthApi;
import com.acme.generated.api.MoviesApi;
import com.acme.generated.model.Meta;
import com.acme.generated.model.MovieDetail;
import com.acme.generated.model.MovieSearchEnvelope;
import com.acme.generated.model.Pagination;
import com.acme.generated.model.PingEnvelope;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
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

  private static final Pattern SEARCH_MOVIES_RESPONSE = Pattern.compile("SearchMovies.*Response.*");

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
  void movieDetailGenresIsAListAndRatingIsABigDecimal() throws NoSuchMethodException {
    Method getGenres = MovieDetail.class.getMethod("getGenres");
    Method getRating = MovieDetail.class.getMethod("getRating");

    assertThat(List.class).isAssignableFrom(getGenres.getReturnType());
    assertThat(getRating.getReturnType()).isEqualTo(BigDecimal.class);
  }

  @Test
  void moviesApiSearchMoviesReturnsTheSharedMovieSearchEnvelope() {
    Method searchMovies = searchMoviesMethod();

    assertThat(searchMovies.getReturnType()).isEqualTo(ResponseEntity.class);
    ParameterizedType genericReturnType = (ParameterizedType) searchMovies.getGenericReturnType();
    assertThat(genericReturnType.getActualTypeArguments())
        .containsExactly(MovieSearchEnvelope.class);
  }

  @Test
  void searchMoviesParametersBindToPlainTypes() {
    Method searchMovies = searchMoviesMethod();

    assertThat(parameter(searchMovies, "genre").getType()).isEqualTo(List.class);
    ParameterizedType genreType =
        (ParameterizedType) parameter(searchMovies, "genre").getParameterizedType();
    assertThat(genreType.getActualTypeArguments()).containsExactly(String.class);
    assertThat(parameter(searchMovies, "minRating").getType()).isEqualTo(BigDecimal.class);
    // A plain String (not a generated enum), so the domain parses it strictly (design D1).
    assertThat(parameter(searchMovies, "sort").getType()).isEqualTo(String.class);
  }

  @Test
  void searchMoviesBoundedParametersCarryBeanValidationConstraints() {
    Method searchMovies = searchMoviesMethod();

    Parameter page = parameter(searchMovies, "page");
    assertThat(page.getAnnotation(Min.class).value()).isZero();

    Parameter size = parameter(searchMovies, "size");
    assertThat(size.getAnnotation(Min.class).value()).isEqualTo(1);
    assertThat(size.getAnnotation(Max.class).value()).isEqualTo(100);

    Parameter minRating = parameter(searchMovies, "minRating");
    assertThat(minRating.getAnnotation(DecimalMin.class).value()).isEqualTo("0");
    assertThat(minRating.getAnnotation(DecimalMax.class).value()).isEqualTo("5");
  }

  /**
   * Pins the observed fact that the generated {@code MoviesApi} carries {@code @Validated} (the
   * generator's default {@code useBeanValidation=true}). If a generator upgrade changes this, the
   * build fails and the change is reviewed against design D3.
   */
  @Test
  void generatedMoviesApiIsAnnotatedValidated() {
    assertThat(MoviesApi.class.isAnnotationPresent(Validated.class)).isTrue();
  }

  @Test
  void metaHasAnOptionalPagination() throws NoSuchMethodException {
    Method getPagination = Meta.class.getMethod("getPagination");

    assertThat(getPagination.getReturnType()).isEqualTo(Pagination.class);
  }

  @Test
  void noPerOperationSearchMoviesResponseModelsAreGenerated() throws IOException {
    assertThat(findModelsMatching(generatedModelDirectory(), SEARCH_MOVIES_RESPONSE)).isEmpty();
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

  private static Method searchMoviesMethod() {
    return Stream.of(MoviesApi.class.getMethods())
        .filter(m -> m.getName().equals("searchMovies"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("MoviesApi.searchMovies is not generated"));
  }

  private static Parameter parameter(Method method, String requestParamName) {
    return Stream.of(method.getParameters())
        .filter(
            p -> {
              RequestParam requestParam = p.getAnnotation(RequestParam.class);
              return requestParam != null && requestParam.value().equals(requestParamName);
            })
        .findFirst()
        .orElseThrow(() -> new AssertionError("no request parameter " + requestParamName));
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
