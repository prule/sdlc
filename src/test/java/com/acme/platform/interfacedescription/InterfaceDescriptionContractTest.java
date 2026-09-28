package com.acme.platform.interfacedescription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.acme.testsupport.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

/**
 * Loads the description actually served over HTTP and validates real response bodies against it
 * (BR-4, design D7). The success schemas are closed ({@code additionalProperties: false}), so an
 * undeclared key in a real body fails validation.
 */
@AutoConfigureMockMvc
class InterfaceDescriptionContractTest extends PostgresIntegrationTest {

  private static final Set<String> HTTP_METHODS =
      Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");

  @Autowired private MockMvc mockMvc;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  @Autowired private JdbcTemplate jdbcTemplate;

  private final ObjectMapper jsonMapper = new ObjectMapper();
  private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
  private final JsonSchemaFactory schemaFactory =
      JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

  private JsonNode document;

  @BeforeEach
  void loadServedDescription() throws Exception {
    MvcResult result = mockMvc.perform(get("/openapi/openapi.bundled.yaml")).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    document = yamlMapper.readTree(result.getResponse().getContentAsString());
  }

  @AfterEach
  void cleanUpMovies() {
    jdbcTemplate.update("DELETE FROM movie_genre");
    jdbcTemplate.update("DELETE FROM movie");
    jdbcTemplate.update("DELETE FROM genre");
  }

  @Test
  void isAWellFormedSelfContainedOpenApi31Document() {
    assertThat(document.get("openapi").asText()).startsWith("3.1");
    assertThat(collectRefValues(document)).allSatisfy(ref -> assertThat(ref).startsWith("#"));
  }

  @Test
  void serversPointAtTheApiV1BasePath() {
    assertThat(document.get("servers").get(0).get("url").asText()).isEqualTo("/api/v1");
  }

  @Test
  void everyDocumentedOperationIsRouted() {
    List<String[]> operations = documentedOperations();

    assertThat(operations)
        .isNotEmpty()
        .allSatisfy(
            operation ->
                assertThat(handlerFor(operation[0], operation[1]))
                    .as("%s %s is documented but not routed", operation[0], operation[1])
                    .isNotNull());
  }

  @Test
  void sharedConceptsAreDefinedOnceUnderTheirOwnNames() {
    Set<String> schemaNames = collectFieldNames(document.at("/components/schemas"));
    assertThat(schemaNames).contains("Problem", "Meta", "Link");
    assertThat(schemaNames).noneMatch(name -> name.matches(".*(_\\d+|\\d+)$"));
    assertThat(collectFieldNames(document.at("/components/headers")))
        .containsExactly("X-Correlation-Id");
  }

  @Test
  void noOtherSchemaDuplicatesTheProblemShape() {
    JsonNode schemas = document.at("/components/schemas");
    Set<String> problemProperties = collectFieldNames(schemas.at("/Problem/properties"));

    assertThat(collectFieldNames(schemas))
        .filteredOn(name -> !name.equals("Problem"))
        .allSatisfy(
            name ->
                assertThat(collectFieldNames(schemas.at("/" + name + "/properties")))
                    .isNotEqualTo(problemProperties));
  }

  @Test
  void everyResponseSchemaIsAReferenceAndEveryProblemIsTheSharedProblem() {
    List<Map.Entry<String, JsonNode>> mediaTypes = responseMediaTypes();

    assertThat(mediaTypes)
        .isNotEmpty()
        .allSatisfy(
            media ->
                assertThat(media.getValue().get("schema").fieldNames())
                    .toIterable()
                    .containsExactly("$ref"));
    assertThat(mediaTypes)
        .filteredOn(media -> media.getKey().equals("application/problem+json"))
        .isNotEmpty()
        .allSatisfy(
            media ->
                assertThat(media.getValue().at("/schema/$ref").asText())
                    .isEqualTo("#/components/schemas/Problem"));
  }

  @Test
  void everyNonSuccessResponseOfPingIsTheSharedProblem() {
    JsonNode responses = document.at("/paths/~1ping/get/responses");

    assertThat(collectFieldNames(responses))
        .filteredOn(status -> !status.startsWith("2"))
        .isNotEmpty()
        .allSatisfy(
            status ->
                assertThat(
                        resolveResponse(responses.get(status))
                            .at("/content/application~1problem+json/schema/$ref")
                            .asText())
                    .isEqualTo("#/components/schemas/Problem"));
  }

  @Test
  void pingSuccessBodyConformsToItsDeclaredSchema() throws Exception {
    String body = performJson(get("/ping"));
    assertNoErrors(validate(body, "PingEnvelope"));
  }

  @Test
  void anExtraKeyInAPingBodyFailsValidation() throws Exception {
    String body = performJson(get("/ping"));
    ObjectNode mutated = (ObjectNode) jsonMapper.readTree(body);
    ((ObjectNode) mutated.get("data")).put("unexpected", "surprise");

    assertThat(validate(mutated.toString(), "PingEnvelope")).isNotEmpty();
  }

  @Test
  void notFoundBodyConformsToTheSharedProblemSchema() throws Exception {
    assertNoErrors(validate(performJson(get("/no-such-thing")), "Problem"));
    assertNoErrors(validate(performJson(put("/no-such-thing")), "Problem"));
  }

  @Test
  void methodNotAllowedBodyConformsToTheSharedProblemSchema() throws Exception {
    assertNoErrors(validate(performJson(post("/ping")), "Problem"));
  }

  @Test
  void notAcceptableBodyConformsToTheSharedProblemSchema() throws Exception {
    assertNoErrors(
        validate(performJson(get("/ping").accept(MediaType.APPLICATION_XML)), "Problem"));
  }

  @Test
  void noProblemBodyCarriesInstance() throws Exception {
    for (String body :
        Set.of(
            performJson(get("/no-such-thing")),
            performJson(post("/ping")),
            performJson(get("/ping").accept(MediaType.APPLICATION_XML)))) {
      assertThat(jsonMapper.readTree(body).has("instance")).isFalse();
    }
  }

  @Test
  void getMovieSuccessBodyConformsToItsDeclaredSchema() throws Exception {
    UUID movieId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO movie (id, title, release_year, runtime_minutes, synopsis, rating) "
            + "VALUES (?, ?, ?, ?, ?, ?)",
        movieId,
        "Arrival",
        2016,
        116,
        "A linguist is recruited.",
        new BigDecimal("4.5"));

    String body = performJson(get("/movies/" + movieId));
    assertNoErrors(validate(body, "MovieEnvelope"));
  }

  @Test
  void getMovieBadRequestBodyConformsToTheSharedProblemSchema() throws Exception {
    assertNoErrors(validate(performJson(get("/movies/not-a-movie-id")), "Problem"));
  }

  @Test
  void getMovieNotFoundBodyConformsToTheSharedProblemSchema() throws Exception {
    assertNoErrors(validate(performJson(get("/movies/" + UUID.randomUUID())), "Problem"));
  }

  @Test
  void getMovieMethodNotAllowedBodyConformsToTheSharedProblemSchema() throws Exception {
    assertNoErrors(validate(performJson(put("/movies/" + UUID.randomUUID())), "Problem"));
  }

  private String performJson(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
      throws Exception {
    return mockMvc.perform(request).andReturn().getResponse().getContentAsString();
  }

  private Set<ValidationMessage> validate(String jsonBody, String schemaName) throws Exception {
    JsonSchema schema = schemaFor(schemaName);
    return schema.validate(jsonMapper.readTree(jsonBody));
  }

  private void assertNoErrors(Set<ValidationMessage> messages) {
    assertThat(messages).isEmpty();
  }

  private JsonSchema schemaFor(String schemaName) {
    ObjectNode wrapper = jsonMapper.createObjectNode();
    wrapper.put("$ref", "#/components/schemas/" + schemaName);
    wrapper.set("components", document.get("components"));
    return schemaFactory.getSchema(wrapper);
  }

  private Set<String> collectFieldNames(JsonNode objectNode) {
    Set<String> names = new java.util.LinkedHashSet<>();
    if (objectNode != null && objectNode.isObject()) {
      objectNode.fieldNames().forEachRemaining(names::add);
    }
    return names;
  }

  /** Walks the whole document collecting every {@code $ref} value found anywhere in it. */
  private java.util.List<String> collectRefValues(JsonNode node) {
    java.util.List<String> refs = new java.util.ArrayList<>();
    Deque<JsonNode> stack = new ArrayDeque<>();
    stack.push(node);
    while (!stack.isEmpty()) {
      JsonNode current = stack.pop();
      if (current.isObject()) {
        Iterator<Map.Entry<String, JsonNode>> fields = current.fields();
        while (fields.hasNext()) {
          Map.Entry<String, JsonNode> field = fields.next();
          if (field.getKey().equals("$ref") && field.getValue().isTextual()) {
            refs.add(field.getValue().asText());
          } else {
            stack.push(field.getValue());
          }
        }
      } else if (current.isArray()) {
        current.forEach(stack::push);
      }
    }
    return refs;
  }

  /** Every documented operation in the served description, as {@code [METHOD, path]} pairs. */
  private List<String[]> documentedOperations() {
    List<String[]> operations = new ArrayList<>();
    document
        .get("paths")
        .fields()
        .forEachRemaining(
            path ->
                path.getValue()
                    .fieldNames()
                    .forEachRemaining(
                        method -> {
                          if (HTTP_METHODS.contains(method)) {
                            operations.add(
                                new String[] {method.toUpperCase(Locale.ROOT), path.getKey()});
                          }
                        }));
    return operations;
  }

  /**
   * The MVC handler routed for a documented operation, or {@code null} if nothing is mapped. Asking
   * the handler mapping directly (rather than calling the operation) separates "not routed" from a
   * routed operation that legitimately answers 404; path template variables are filled with a
   * placeholder UUID.
   */
  private Object handlerFor(String method, String pathTemplate) throws Exception {
    MockHttpServletRequest request =
        new MockHttpServletRequest(
            method, pathTemplate.replaceAll("\\{[^}]+}", "00000000-0000-0000-0000-000000000000"));
    ServletRequestPathUtils.parseAndCache(request);
    HandlerExecutionChain chain = handlerMapping.getHandler(request);
    return chain == null ? null : chain.getHandler();
  }

  /** Every media-type entry of every response, inline under an operation or shared. */
  private List<Map.Entry<String, JsonNode>> responseMediaTypes() {
    List<JsonNode> responses = new ArrayList<>();
    document
        .get("paths")
        .forEach(
            pathItem ->
                pathItem.forEach(operation -> operation.path("responses").forEach(responses::add)));
    document.at("/components/responses").forEach(responses::add);

    List<Map.Entry<String, JsonNode>> mediaTypes = new ArrayList<>();
    responses.forEach(
        response -> response.path("content").fields().forEachRemaining(mediaTypes::add));
    return mediaTypes;
  }

  /** Follows a response {@code $ref} to its shared component, or returns the inline response. */
  private JsonNode resolveResponse(JsonNode response) {
    JsonNode ref = response.get("$ref");
    return ref == null ? response : document.at(ref.asText().substring(1));
  }
}
