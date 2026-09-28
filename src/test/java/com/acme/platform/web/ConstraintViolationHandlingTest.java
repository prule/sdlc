package com.acme.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Web-slice coverage for {@link GlobalExceptionHandler}'s {@code ConstraintViolationException} and
 * type-mismatch handling, through a test-only {@code @Validated} interface mirroring the generated
 * one (design D2, task 6.1). The {@code @WebMvcTest} slice applies Boot's own {@code
 * ValidationAutoConfiguration}, so the method-validation proxy is the production one.
 */
@PlatformWebTest(controllers = TestOnlyValidatedController.class)
@Import(TestOnlyConstrainedService.class)
class ConstraintViolationHandlingTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ProblemFactory problemFactory;

  @Test
  void outOfRangePageIsBadRequestNamingPage() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/test-only/validated/bounds").param("page", "-1").param("size", "20"))
            .andReturn();

    JsonNode body = assertBadRequest(result);
    assertThat(body.get("detail").asText()).isEqualTo("Query parameter 'page' is invalid.");
  }

  @Test
  void outOfRangeSizeIsBadRequestNamingSize() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/test-only/validated/bounds").param("page", "0").param("size", "101"))
            .andReturn();

    JsonNode body = assertBadRequest(result);
    assertThat(body.get("detail").asText()).isEqualTo("Query parameter 'size' is invalid.");
  }

  @Test
  void whenBothParametersAreViolatedTheLowerIndexOneIsNamed() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/test-only/validated/bounds").param("page", "-1").param("size", "101"))
            .andReturn();

    JsonNode body = assertBadRequest(result);
    assertThat(body.get("detail").asText()).isEqualTo("Query parameter 'page' is invalid.");
  }

  @Test
  void nonNumericPageIsBadRequestNamingPageWithoutLeakingTheValueOrType() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/test-only/validated/bounds").param("page", "abc").param("size", "20"))
            .andReturn();

    JsonNode body = assertBadRequest(result);
    assertThat(body.get("detail").asText()).isEqualTo("Query parameter 'page' is invalid.");
    assertThat(body.get("detail").asText())
        .doesNotContain("abc")
        .doesNotContainIgnoringCase("integer");
  }

  @Test
  void intOverflowPageIsBadRequestNamingPage() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/test-only/validated/bounds").param("page", "99999999999").param("size", "20"))
            .andReturn();

    JsonNode body = assertBadRequest(result);
    assertThat(body.get("detail").asText()).isEqualTo("Query parameter 'page' is invalid.");
  }

  @Test
  void nonIntegerSizeIsBadRequestNamingSize() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/test-only/validated/bounds").param("page", "0").param("size", "1.5"))
            .andReturn();

    JsonNode body = assertBadRequest(result);
    assertThat(body.get("detail").asText()).isEqualTo("Query parameter 'size' is invalid.");
  }

  @Test
  void aRequestParamDeclaredWithTheNameAttributeIsNamedForBoundsAndTypeFailures() throws Exception {
    MvcResult bounds =
        mockMvc
            .perform(get("/test-only/validated/name-attribute").param("offset", "-1"))
            .andReturn();
    MvcResult type =
        mockMvc
            .perform(get("/test-only/validated/name-attribute").param("offset", "x"))
            .andReturn();

    assertThat(assertBadRequest(bounds).get("detail").asText())
        .isEqualTo("Query parameter 'offset' is invalid.");
    assertThat(assertBadRequest(type).get("detail").asText())
        .isEqualTo("Query parameter 'offset' is invalid.");
  }

  @Test
  void constrainedPathVariableGivesTheGenericDetail() throws Exception {
    MvcResult result = mockMvc.perform(get("/test-only/validated/path/{id}", -1)).andReturn();

    JsonNode body = assertBadRequest(result);
    assertThat(body.get("detail").asText()).isEqualTo("The request could not be understood.");
  }

  @Test
  void aViolationFromAConstrainedServiceBeanAtTheSameIndexIsInternalErrorNotABadRequest()
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/test-only/validated/service-conflict").param("page", "0").param("size", "0"))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(500);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("code").asText()).isEqualTo("INTERNAL_ERROR");
  }

  @Test
  void invalidQueryParameterExceptionMapsTheSameWay() {
    var problem = problemFactory.invalidQueryParameter("x");

    assertThat(problem.getStatus()).isEqualTo(400);
    assertThat(problem.getDetail()).isEqualTo("Query parameter 'x' is invalid.");
  }

  @Test
  void aConstraintViolationExceptionWithNoHandlerMethodIsInternalError() {
    GlobalExceptionHandler handler = new GlobalExceptionHandler(problemFactory);
    ConstraintViolationException ex = realConstraintViolationException();

    var response = handler.handleConstraintViolation(ex, null);

    assertThat(response.getStatusCode().value()).isEqualTo(500);
  }

  private ConstraintViolationException realConstraintViolationException() {
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    Set<ConstraintViolation<ConstrainedBean>> violations =
        validator.validate(new ConstrainedBean(-1));
    return new ConstraintViolationException(violations);
  }

  private record ConstrainedBean(@Min(0) int value) {}

  private JsonNode assertBadRequest(MvcResult result) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("code").asText()).isEqualTo("BAD_REQUEST");
    return body;
  }
}
