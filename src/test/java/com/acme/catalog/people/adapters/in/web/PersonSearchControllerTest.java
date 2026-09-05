package com.acme.catalog.people.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.catalog.people.application.port.in.GetPersonByIdUseCase;
import com.acme.catalog.people.application.port.in.GetPersonFilmographyUseCase;
import com.acme.catalog.people.application.port.in.SearchPeopleUseCase;
import com.acme.catalog.people.domain.model.Person;
import com.acme.catalog.people.domain.model.PersonId;
import com.acme.catalog.people.domain.model.PersonPage;
import com.acme.catalog.people.domain.model.PersonSearchCriteria;
import com.acme.catalog.people.domain.model.PersonSort;
import com.acme.catalog.people.domain.model.PersonSortField;
import com.acme.catalog.people.domain.model.SortDirection;
import com.acme.common.error.GlobalExceptionHandler;
import com.acme.common.security.ProblemAccessDeniedHandler;
import com.acme.common.security.ProblemAuthenticationEntryPoint;
import com.acme.common.security.SecurityConfig;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-slice test for {@link PersonController#listPeople}. {@link SearchPeopleUseCase} is mocked at
 * the port seam.
 */
@WebMvcTest(PersonController.class)
@Import({
  SecurityConfig.class,
  ProblemAuthenticationEntryPoint.class,
  ProblemAccessDeniedHandler.class,
  GlobalExceptionHandler.class
})
class PersonSearchControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private GetPersonByIdUseCase getPersonByIdUseCase;
  @MockitoBean private GetPersonFilmographyUseCase getPersonFilmographyUseCase;
  @MockitoBean private SearchPeopleUseCase searchPeopleUseCase;

  private static Person person(int i) {
    return new Person(
        new PersonId(UUID.nameUUIDFromBytes(("person-" + i).getBytes())), "Person " + i);
  }

  private static PersonPage pageOf(int page, int size, long totalElements, List<Person> items) {
    int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
    return new PersonPage(items, page, size, totalElements, totalPages);
  }

  @Test
  void listPeople_defaultRequest_returnsHalCollectionShape() throws Exception {
    given(
            searchPeopleUseCase.searchPeople(
                eq(PersonSearchCriteria.NONE), eq(0), eq(20), eq(PersonSort.DEFAULT)))
        .willReturn(pageOf(0, 20, 1, List.of(person(1))));

    mockMvc
        .perform(get("/people"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(header().exists("X-Correlation-Id"))
        .andExpect(jsonPath("$.data._embedded.people.length()").value(1))
        .andExpect(jsonPath("$.data._links.self.href").exists())
        .andExpect(jsonPath("$.meta.pagination.page").value(0))
        .andExpect(jsonPath("$.meta.pagination.size").value(20))
        .andExpect(jsonPath("$.meta.pagination.totalElements").value(1))
        .andExpect(jsonPath("$.meta.pagination.totalPages").value(1));
  }

  @Test
  void listPeople_item_exposesExactlyIdNameAndSelfLink() throws Exception {
    given(searchPeopleUseCase.searchPeople(any(), anyInt(), anyInt(), any()))
        .willReturn(pageOf(0, 20, 1, List.of(person(1))));

    String body =
        mockMvc
            .perform(get("/people"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data._embedded.people[0].id").exists())
            .andExpect(jsonPath("$.data._embedded.people[0].name").value("Person 1"))
            .andExpect(jsonPath("$.data._embedded.people[0]._links.self.href").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();

    com.fasterxml.jackson.databind.JsonNode item =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(body)
            .path("data")
            .path("_embedded")
            .path("people")
            .get(0);

    java.util.List<String> fieldNames = new java.util.ArrayList<>();
    item.fieldNames().forEachRemaining(fieldNames::add);
    assertThat(fieldNames).containsExactlyInAnyOrder("id", "name", "_links");
    assertThat(item.has("_embedded")).isFalse();
    assertThat(item.has("_templates")).isFalse();
  }

  @Test
  void listPeople_middlePage_hasAllNavigationLinks() throws Exception {
    given(searchPeopleUseCase.searchPeople(any(), eq(1), eq(5), any()))
        .willReturn(pageOf(1, 5, 25, List.of(person(1))));

    mockMvc
        .perform(get("/people").param("page", "1").param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._links.self.href").exists())
        .andExpect(jsonPath("$.data._links.first.href").exists())
        .andExpect(jsonPath("$.data._links.last.href").exists())
        .andExpect(jsonPath("$.data._links.prev.href").exists())
        .andExpect(jsonPath("$.data._links.next.href").exists());
  }

  @Test
  void listPeople_firstPage_omitsPrev() throws Exception {
    given(searchPeopleUseCase.searchPeople(any(), eq(0), eq(5), any()))
        .willReturn(pageOf(0, 5, 25, List.of(person(1))));

    mockMvc
        .perform(get("/people").param("page", "0").param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._links.next.href").exists())
        .andExpect(jsonPath("$.data._links.prev").doesNotExist());
  }

  @Test
  void listPeople_lastPage_omitsNext() throws Exception {
    given(searchPeopleUseCase.searchPeople(any(), eq(4), eq(5), any()))
        .willReturn(pageOf(4, 5, 25, List.of(person(1))));

    mockMvc
        .perform(get("/people").param("page", "4").param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._links.prev.href").exists())
        .andExpect(jsonPath("$.data._links.next").doesNotExist());
  }

  @Test
  void listPeople_emptyResult_isOkWithNoNextOrPrevAndFirstLastAtPageZero() throws Exception {
    given(searchPeopleUseCase.searchPeople(any(), eq(0), eq(20), any()))
        .willReturn(pageOf(0, 20, 0, List.of()));

    mockMvc
        .perform(get("/people"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._embedded.people.length()").value(0))
        .andExpect(jsonPath("$.meta.pagination.totalElements").value(0))
        .andExpect(jsonPath("$.meta.pagination.totalPages").value(0))
        .andExpect(jsonPath("$.data._links.self.href").exists())
        .andExpect(jsonPath("$.data._links.first.href").exists())
        .andExpect(jsonPath("$.data._links.last.href").exists())
        .andExpect(jsonPath("$.data._links.next").doesNotExist())
        .andExpect(jsonPath("$.data._links.prev").doesNotExist());
  }

  @Test
  void listPeople_pageBeyondLastPage_isOkWithEmptyEmbeddedNotAnError() throws Exception {
    given(searchPeopleUseCase.searchPeople(any(), eq(9), eq(5), any()))
        .willReturn(pageOf(9, 5, 25, List.of()));

    mockMvc
        .perform(get("/people").param("page", "9").param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._embedded.people.length()").value(0))
        .andExpect(jsonPath("$.data._links.next").doesNotExist());
  }

  @Test
  void listPeople_nameAndSort_arePassedToTheUseCaseAndPreservedInNavigationLinks()
      throws Exception {
    given(searchPeopleUseCase.searchPeople(any(), anyInt(), anyInt(), any()))
        .willReturn(pageOf(0, 5, 25, List.of(person(1))));

    mockMvc
        .perform(
            get("/people").param("name", "keanu").param("sort", "name,desc").param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data._links.self.href", org.hamcrest.Matchers.containsString("name=keanu")))
        .andExpect(
            jsonPath(
                "$.data._links.self.href",
                org.hamcrest.Matchers.containsString("sort=name%2Cdesc")))
        .andExpect(
            jsonPath("$.data._links.next.href", org.hamcrest.Matchers.containsString("name=keanu")))
        .andExpect(
            jsonPath(
                "$.data._links.next.href",
                org.hamcrest.Matchers.containsString("sort=name%2Cdesc")));

    verify(searchPeopleUseCase)
        .searchPeople(
            eq(new PersonSearchCriteria(Optional.of("keanu"))),
            eq(0),
            eq(5),
            eq(new PersonSort(PersonSortField.NAME, SortDirection.DESC)));
  }

  @Test
  void listPeople_unknownSortField_isRejectedWith400ProblemJsonNotServerError() throws Exception {
    mockMvc
        .perform(get("/people").param("sort", "bogus,asc"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").exists())
        .andExpect(jsonPath("$.correlationId").exists());
  }

  @Test
  void listPeople_unknownSortDirection_isRejectedWith400() throws Exception {
    mockMvc
        .perform(get("/people").param("sort", "name,sideways"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void listPeople_negativePage_isRejectedWith400() throws Exception {
    mockMvc
        .perform(get("/people").param("page", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").exists())
        .andExpect(jsonPath("$.correlationId").exists());
  }

  @Test
  void listPeople_sizeZero_isRejectedWith400() throws Exception {
    mockMvc.perform(get("/people").param("size", "0")).andExpect(status().isBadRequest());
  }

  @Test
  void listPeople_sizeAboveMaximum_isRejectedWith400() throws Exception {
    mockMvc.perform(get("/people").param("size", "101")).andExpect(status().isBadRequest());
  }

  @Test
  void listPeople_nonNumericSize_isRejectedWith400NotServerError() throws Exception {
    mockMvc
        .perform(get("/people").param("size", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void listPeople_noAuthorizationHeader_isNever401Or403() throws Exception {
    given(searchPeopleUseCase.searchPeople(any(), anyInt(), anyInt(), any()))
        .willReturn(pageOf(0, 20, 0, List.of()));

    mockMvc.perform(get("/people")).andExpect(status().isOk());
  }
}
