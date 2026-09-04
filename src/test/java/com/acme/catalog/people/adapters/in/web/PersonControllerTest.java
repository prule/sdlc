package com.acme.catalog.people.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.catalog.people.application.port.in.GetPersonByIdUseCase;
import com.acme.catalog.people.domain.model.Person;
import com.acme.catalog.people.domain.model.PersonId;
import com.acme.common.error.GlobalExceptionHandler;
import com.acme.common.error.ResourceNotFoundException;
import com.acme.common.security.ProblemAccessDeniedHandler;
import com.acme.common.security.ProblemAuthenticationEntryPoint;
import com.acme.common.security.SecurityConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-slice test for {@link PersonController}. {@link GetPersonByIdUseCase} is mocked at the port
 * seam.
 */
@WebMvcTest(PersonController.class)
@Import({
  SecurityConfig.class,
  ProblemAuthenticationEntryPoint.class,
  ProblemAccessDeniedHandler.class,
  GlobalExceptionHandler.class
})
class PersonControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private GetPersonByIdUseCase getPersonByIdUseCase;

  @Test
  void getPersonById_existingPerson_returns200EnvelopedDetailWithSelfLink() throws Exception {
    UUID id = UUID.randomUUID();
    Person person = new Person(new PersonId(id), "Keanu Reeves");
    given(getPersonByIdUseCase.getPersonById(new PersonId(id))).willReturn(person);

    mockMvc
        .perform(get("/people/{id}", id))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(header().exists("X-Correlation-Id"))
        .andExpect(jsonPath("$.data.id").value(id.toString()))
        .andExpect(jsonPath("$.data.name").value("Keanu Reeves"))
        .andExpect(jsonPath("$.data._links.self.href").exists())
        .andExpect(
            jsonPath("$.data._links.self.href")
                .value(org.hamcrest.Matchers.endsWith("/people/" + id)))
        .andExpect(jsonPath("$.data._embedded").doesNotExist())
        .andExpect(jsonPath("$.meta.correlationId").exists())
        .andExpect(jsonPath("$.meta.timestamp").exists());
  }

  @Test
  void getPersonById_onlyIdNameLinksPresent_noEmbeddedNoTemplatesNoBioFields() throws Exception {
    UUID id = UUID.randomUUID();
    Person person = new Person(new PersonId(id), "Keanu Reeves");
    given(getPersonByIdUseCase.getPersonById(new PersonId(id))).willReturn(person);

    var result = mockMvc.perform(get("/people/{id}", id)).andExpect(status().isOk()).andReturn();
    JsonNode data =
        new ObjectMapper().readTree(result.getResponse().getContentAsString()).path("data");

    assertThat(data.fieldNames()).toIterable().containsExactlyInAnyOrder("id", "name", "_links");
    JsonNode links = data.path("_links");
    assertThat(links.fieldNames()).toIterable().containsExactly("self");
    assertThat(data.has("_embedded")).isFalse();
    assertThat(data.has("_templates")).isFalse();
  }

  @Test
  void getPersonById_unknownId_returns404ProblemJsonNoLinks() throws Exception {
    UUID id = UUID.randomUUID();
    given(getPersonByIdUseCase.getPersonById(new PersonId(id)))
        .willThrow(
            new ResourceNotFoundException("PERSON_NOT_FOUND", "No person found for id: " + id));

    mockMvc
        .perform(get("/people/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("PERSON_NOT_FOUND"))
        .andExpect(jsonPath("$.correlationId").exists())
        .andExpect(jsonPath("$._links").doesNotExist())
        .andExpect(jsonPath("$._embedded").doesNotExist());
  }

  @Test
  void getPersonById_malformedUuid_returns400ProblemJsonNotServerError() throws Exception {
    mockMvc
        .perform(get("/people/{id}", "not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").exists())
        .andExpect(jsonPath("$.correlationId").exists());
  }

  @Test
  void getPersonById_noAuthorizationHeader_isNever401Or403() throws Exception {
    UUID id = UUID.randomUUID();
    Person person = new Person(new PersonId(id), "Keanu Reeves");
    given(getPersonByIdUseCase.getPersonById(new PersonId(id))).willReturn(person);

    mockMvc.perform(get("/people/{id}", id)).andExpect(status().isOk());
  }
}
