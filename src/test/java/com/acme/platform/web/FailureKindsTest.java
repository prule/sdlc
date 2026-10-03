package com.acme.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.platform.availability.adapters.in.web.PingController;
import com.acme.platform.availability.application.port.in.CheckAvailabilityUseCase;
import com.acme.platform.availability.domain.model.Availability;
import com.acme.platform.availability.domain.model.AvailabilityStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Every failure kind falls into exactly one status/code/type, per the table in design D3. Each test
 * asserts every required {@code Problem} member, the absence of {@code instance}, and that no
 * internal detail (exception message, class name, trace) leaks into the body.
 */
@PlatformWebTest(controllers = {PingController.class, TestOnlyController.class})
class FailureKindsTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private CheckAvailabilityUseCase checkAvailabilityUseCase;

  @Test
  void unknownPathWithGetIsNotFound() throws Exception {
    assertProblem(
        mockMvc.perform(get("/no-such-thing")).andReturn(),
        404,
        "NOT_FOUND",
        "urn:problem-type:not-found");
  }

  @Test
  void unknownPathWithPutIsNotFound() throws Exception {
    assertProblem(
        mockMvc.perform(put("/no-such-thing")).andReturn(),
        404,
        "NOT_FOUND",
        "urn:problem-type:not-found");
  }

  @Test
  void methodNotAllowedOnPingListsGetInAllow() throws Exception {
    MvcResult result = mockMvc.perform(post("/ping")).andReturn();
    assertProblem(result, 405, "METHOD_NOT_ALLOWED", "urn:problem-type:method-not-allowed");
    assertThat(result.getResponse().getHeader("Allow")).contains("GET");
  }

  @Test
  void methodNotAllowedOnAnInterfaceDescriptionAssetListsGetAndHeadExactly() throws Exception {
    for (String path : new String[] {"/openapi/openapi.bundled.yaml", "/swagger-ui/index.html"}) {
      MvcResult result = mockMvc.perform(post(path)).andReturn();
      assertProblem(result, 405, "METHOD_NOT_ALLOWED", "urn:problem-type:method-not-allowed");
      assertThat(result.getResponse().getHeader("Allow").replace(" ", "")).isEqualTo("GET,HEAD");
    }
  }

  @Test
  void notAcceptableOnPingIsAProblemNotAServerFault() throws Exception {
    BDDMockito.given(checkAvailabilityUseCase.checkAvailability())
        .willReturn(new Availability(AvailabilityStatus.UP));

    assertProblem(
        mockMvc.perform(get("/ping").accept(MediaType.APPLICATION_XML)).andReturn(),
        406,
        "NOT_ACCEPTABLE",
        "urn:problem-type:not-acceptable");
  }

  @Test
  void pingAcceptingOnlyProblemJsonStillGetsTheSuccessEnvelopeAsJson() throws Exception {
    BDDMockito.given(checkAvailabilityUseCase.checkAvailability())
        .willReturn(new Availability(AvailabilityStatus.UP));

    mockMvc
        .perform(get("/ping").accept(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON));
  }

  @Test
  void unsupportedRequestMediaTypeIs415() throws Exception {
    assertProblem(
        mockMvc
            .perform(
                post("/test-only/consumes-json")
                    .contentType(MediaType.APPLICATION_XML)
                    .content("<a/>"))
            .andReturn(),
        415,
        "UNSUPPORTED_MEDIA_TYPE",
        "urn:problem-type:unsupported-media-type");
  }

  @Test
  void missingRequiredParameterIs400WithoutLeakingTheParameterType() throws Exception {
    MvcResult result = mockMvc.perform(get("/test-only/uuid-param")).andReturn();
    JsonNode body = assertProblem(result, 400, "BAD_REQUEST", "urn:problem-type:bad-request");
    assertThat(body.get("detail").asText()).doesNotContainIgnoringCase("uuid");
    assertThat(body.at("/errors/0/field").asText()).isEqualTo("id");
    assertThat(body.get("errors")).hasSize(1);
  }

  @Test
  void malformedParameterIs400WithoutLeakingTheParameterType() throws Exception {
    MvcResult result =
        mockMvc.perform(get("/test-only/uuid-param").param("id", "not-a-uuid")).andReturn();
    JsonNode body = assertProblem(result, 400, "BAD_REQUEST", "urn:problem-type:bad-request");
    assertThat(body.get("detail").asText()).doesNotContainIgnoringCase("uuid");
    assertThat(body.at("/errors/0/field").asText()).isEqualTo("id");
    assertThat(body.at("/errors/0/message").asText()).doesNotContainIgnoringCase("uuid");
    assertThat(result.getResponse().getContentAsString()).doesNotContain("not-a-uuid");
  }

  @Test
  void aBadRequestNotCausedByANamedParameterHasNoErrors() throws Exception {
    MvcResult result = mockMvc.perform(get("/test-only/conflict")).andReturn();
    JsonNode body = assertProblem(result, 400, "BAD_REQUEST", "urn:problem-type:bad-request");
    assertThat(body.has("errors")).isFalse();
  }

  @Test
  void otherFailureKindsNeverCarryErrors() throws Exception {
    BDDMockito.given(checkAvailabilityUseCase.checkAvailability())
        .willReturn(new Availability(AvailabilityStatus.UP));
    MvcResult[] results = {
      mockMvc.perform(get("/no-such-thing")).andReturn(),
      mockMvc.perform(post("/ping")).andReturn(),
      mockMvc.perform(get("/ping").accept(MediaType.APPLICATION_XML)).andReturn(),
      mockMvc
          .perform(
              post("/test-only/consumes-json")
                  .contentType(MediaType.APPLICATION_XML)
                  .content("<a/>"))
          .andReturn(),
      mockMvc.perform(get("/test-only/throws")).andReturn(),
    };
    for (MvcResult result : results) {
      assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).has("errors"))
          .as("status %d", result.getResponse().getStatus())
          .isFalse();
    }
  }

  @Test
  void unexpectedFaultRevealsNothing() throws Exception {
    MvcResult result = mockMvc.perform(get("/test-only/throws")).andReturn();
    JsonNode body = assertProblem(result, 500, "INTERNAL_ERROR", "urn:problem-type:internal-error");
    assertThat(body.get("detail").asText()).isEqualTo("An unexpected error occurred.");
    String raw = result.getResponse().getContentAsString();
    assertThat(raw).doesNotContain("secret-db-host");
  }

  @Test
  void resourceNotFoundExceptionIsNotFoundWithoutLeakingItsMessage() throws Exception {
    MvcResult result = mockMvc.perform(get("/test-only/not-found")).andReturn();
    assertProblem(result, 404, "NOT_FOUND", "urn:problem-type:not-found");
    assertThat(result.getResponse().getContentAsString()).doesNotContain("secret-internal-id-42");
  }

  @Test
  void anOtherClientStatusCollapsesToBadRequestOnTheWireAndInTheBody() throws Exception {
    MvcResult result = mockMvc.perform(get("/test-only/conflict")).andReturn();
    assertProblem(result, 400, "BAD_REQUEST", "urn:problem-type:bad-request");
    assertThat(result.getResponse().getContentAsString()).doesNotContain("row 42");
  }

  @Test
  void anOtherServerStatusCollapsesToInternalErrorOnTheWireAndInTheBody() throws Exception {
    MvcResult result = mockMvc.perform(get("/test-only/unavailable")).andReturn();
    assertProblem(result, 500, "INTERNAL_ERROR", "urn:problem-type:internal-error");
    assertThat(result.getResponse().getContentAsString()).doesNotContain("pool exhausted");
  }

  @Test
  void everyFailureKindHasADistinctTypeAndCode() throws Exception {
    JsonNode notFound =
        assertProblem(
            mockMvc.perform(get("/no-such-thing")).andReturn(),
            404,
            "NOT_FOUND",
            "urn:problem-type:not-found");
    JsonNode methodNotAllowed =
        assertProblem(
            mockMvc.perform(post("/ping")).andReturn(),
            405,
            "METHOD_NOT_ALLOWED",
            "urn:problem-type:method-not-allowed");
    JsonNode badRequest =
        assertProblem(
            mockMvc.perform(get("/test-only/uuid-param")).andReturn(),
            400,
            "BAD_REQUEST",
            "urn:problem-type:bad-request");
    JsonNode internalError =
        assertProblem(
            mockMvc.perform(get("/test-only/throws")).andReturn(),
            500,
            "INTERNAL_ERROR",
            "urn:problem-type:internal-error");

    assertThat(
            java.util.Set.of(
                notFound.get("type").asText(),
                methodNotAllowed.get("type").asText(),
                badRequest.get("type").asText(),
                internalError.get("type").asText()))
        .hasSize(4);
    assertThat(
            java.util.Set.of(
                notFound.get("code").asText(),
                methodNotAllowed.get("code").asText(),
                badRequest.get("code").asText(),
                internalError.get("code").asText()))
        .hasSize(4);
  }

  private JsonNode assertProblem(MvcResult result, int status, String code, String type)
      throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
    assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("type").asText()).isEqualTo(type);
    assertThat(body.hasNonNull("title")).isTrue();
    assertThat(body.hasNonNull("detail")).isTrue();
    assertThat(body.get("status").asInt()).isEqualTo(status);
    assertThat(body.get("code").asText()).isEqualTo(code);
    assertThat(body.hasNonNull("correlationId")).isTrue();
    assertThat(body.has("instance")).isFalse();
    assertThat(body.has("_links")).isFalse();
    assertThat(body.has("_embedded")).isFalse();

    String raw = result.getResponse().getContentAsString();
    assertThat(raw)
        .doesNotContain("Exception")
        .doesNotContain("java.")
        .doesNotContain("org.springframework");

    return body;
  }
}
