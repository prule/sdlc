package com.acme.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.catalog.movies.adapters.in.web.MovieController;
import jakarta.validation.constraints.Max;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedMethod;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * The {@link HandlerMethodValidationException} mapping is a safety net (design D3): with
 * {@code @Validated} controllers the AOP path normally raises a {@code
 * ConstraintViolationException} instead, so this builds the exception directly to prove it, too, is
 * a named {@code 400}.
 */
class GlobalExceptionHandlerSafetyNetTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new ProblemFactory());

  private static Method searchMovies() throws NoSuchMethodException {
    return MovieController.class.getMethod(
        "searchMovies",
        String.class,
        List.class,
        Integer.class,
        Integer.class,
        BigDecimal.class,
        String.class,
        Integer.class,
        Integer.class);
  }

  private static ParameterValidationResult invalid(MethodParameter parameter, Object value) {
    return new ParameterValidationResult(
        parameter,
        value,
        List.of(new DefaultMessageSourceResolvable("Max")),
        null,
        null,
        null,
        (error, sourceType) -> error);
  }

  private ProblemDetail handle(Method method, List<ParameterValidationResult> results)
      throws Exception {
    HandlerMethodValidationException ex =
        new HandlerMethodValidationException(
            MethodValidationResult.create(new Object(), method, results));
    ResponseEntity<Object> response =
        handler.handleException(
            ex, new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()));
    assertThat(response).isNotNull();
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    return (ProblemDetail) response.getBody();
  }

  @Test
  void namesThePublishedNameDeclaredOnTheGeneratedInterface() throws Exception {
    MethodParameter[] parameters = new AnnotatedMethod(searchMovies()).getMethodParameters();

    ProblemDetail problem = handle(searchMovies(), List.of(invalid(parameters[7], 101)));

    assertThat(problem.getDetail()).isEqualTo("The request parameter 'size' is not valid.");
    assertThat(problem.getProperties()).containsEntry("code", "BAD_REQUEST");
  }

  @Test
  void namesTheInvalidParameterWithTheLowestIndex() throws Exception {
    MethodParameter[] parameters = new AnnotatedMethod(searchMovies()).getMethodParameters();

    ProblemDetail problem =
        handle(searchMovies(), List.of(invalid(parameters[7], 0), invalid(parameters[6], -1)));

    assertThat(problem.getDetail()).isEqualTo("The request parameter 'page' is not valid.");
  }

  static class Unannotated {
    public void take(@Max(1) int javaName) {}
  }

  @Test
  void aParameterWithNoPublishedNameGetsTheGenericDetail() throws Exception {
    Method take = Unannotated.class.getMethod("take", int.class);

    ProblemDetail problem = handle(take, List.of(invalid(new MethodParameter(take, 0), 2)));

    assertThat(problem.getDetail()).isEqualTo("The request could not be understood.");
  }
}
