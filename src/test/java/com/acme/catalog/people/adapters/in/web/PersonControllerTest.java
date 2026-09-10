package com.acme.catalog.people.adapters.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.catalog.people.application.port.in.GetPersonDetailUseCase;
import com.acme.catalog.people.application.port.in.GetPersonFilmographyUseCase;
import com.acme.catalog.people.domain.model.Person;
import com.acme.common.error.GlobalExceptionHandler;
import com.acme.common.error.ResourceNotFoundException;
import com.acme.common.security.ProblemAccessDeniedHandler;
import com.acme.common.security.ProblemAuthenticationEntryPoint;
import com.acme.common.security.SecurityConfig;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-slice test for {@link PersonController} (UC-004). {@link GetPersonDetailUseCase} is mocked at
 * the port seam.
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

  @MockitoBean private GetPersonDetailUseCase getPersonDetailUseCase;

  @MockitoBean private GetPersonFilmographyUseCase getPersonFilmographyUseCase;

  @Test
  void getPersonById_existingPerson_returns200WithIdAndNameAndLinksAndNoBiographicalFields()
      throws Exception {
    UUID id = UUID.randomUUID();
    Person person = new Person(id, "Ava Solano");
    given(getPersonDetailUseCase.getPersonDetail(id)).willReturn(person);

    mockMvc
        .perform(get("/people/{id}", id))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.data.id").value(id.toString()))
        .andExpect(jsonPath("$.data.name").value("Ava Solano"))
        .andExpect(jsonPath("$.data._links.self.href", Matchers.endsWith("/people/" + id)))
        .andExpect(
            jsonPath("$.data._links.credits.href", Matchers.endsWith("/people/" + id + "/credits")))
        .andExpect(jsonPath("$.meta.correlationId").exists())
        .andExpect(jsonPath("$.meta.timestamp").exists());
  }

  @Test
  void getPersonById_existingPerson_responseCarriesNoBiographicalFieldKeys() throws Exception {
    UUID id = UUID.randomUUID();
    Person person = new Person(id, "Ava Solano");
    given(getPersonDetailUseCase.getPersonDetail(id)).willReturn(person);

    String body =
        mockMvc.perform(get("/people/{id}", id)).andReturn().getResponse().getContentAsString();

    com.fasterxml.jackson.databind.JsonNode data =
        new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).path("data");

    org.assertj.core.api.Assertions.assertThat(data.has("birthDate")).isFalse();
    org.assertj.core.api.Assertions.assertThat(data.has("biography")).isFalse();
    org.assertj.core.api.Assertions.assertThat(data.has("images")).isFalse();
  }

  @Test
  void getPersonById_wellFormedUnknownId_returns404ProblemJson() throws Exception {
    UUID id = UUID.randomUUID();
    given(getPersonDetailUseCase.getPersonDetail(id))
        .willThrow(
            new ResourceNotFoundException("PERSON_NOT_FOUND", "No person found with id " + id));

    mockMvc
        .perform(get("/people/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("PERSON_NOT_FOUND"))
        .andExpect(jsonPath("$.correlationId").exists());
  }

  @Test
  void getPersonById_malformedId_returns400WithoutInvokingTheUseCase() throws Exception {
    mockMvc
        .perform(get("/people/{id}", "not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").exists())
        .andExpect(jsonPath("$.correlationId").exists());

    verifyNoInteractions(getPersonDetailUseCase);
  }

  @Test
  void getPersonById_requiresNoAuthentication() throws Exception {
    UUID id = UUID.randomUUID();
    Person person = new Person(id, "Ava Solano");
    given(getPersonDetailUseCase.getPersonDetail(any())).willReturn(person);

    mockMvc.perform(get("/people/{id}", id)).andExpect(status().isOk());
  }
}
