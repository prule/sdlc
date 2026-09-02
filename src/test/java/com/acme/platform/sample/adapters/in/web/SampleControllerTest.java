package com.acme.platform.sample.adapters.in.web;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.common.error.GlobalExceptionHandler;
import com.acme.common.security.ProblemAccessDeniedHandler;
import com.acme.common.security.ProblemAuthenticationEntryPoint;
import com.acme.common.security.SecurityConfig;
import com.acme.platform.sample.application.port.in.ListSamplesUseCase;
import com.acme.platform.sample.domain.model.SampleItem;
import com.acme.platform.sample.domain.model.SamplePage;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-slice test for {@link SampleController}, the demonstrative, throwaway sample collection
 * proving the HAL pagination-link convention (see {@code openspec/changes/adopt-hal-hypermedia}).
 * {@link ListSamplesUseCase} is mocked at the port seam.
 */
@WebMvcTest(SampleController.class)
@Import({
  SecurityConfig.class,
  ProblemAuthenticationEntryPoint.class,
  ProblemAccessDeniedHandler.class,
  GlobalExceptionHandler.class
})
class SampleControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ListSamplesUseCase listSamplesUseCase;

  private static SampleItem item(int i) {
    return new SampleItem(UUID.nameUUIDFromBytes(("sample-" + i).getBytes()), "Sample item " + i);
  }

  private static SamplePage pageOf(int page, int size, int totalElements, List<SampleItem> items) {
    int totalPages = (int) Math.ceil((double) totalElements / size);
    return new SamplePage(items, page, size, totalElements, totalPages);
  }

  @Test
  void listSamples_middlePage_hasEmbeddedItemsAndAllNavigationLinks() throws Exception {
    given(listSamplesUseCase.listSamples(1, 5))
        .willReturn(pageOf(1, 5, 25, List.of(item(6), item(7), item(8), item(9), item(10))));

    mockMvc
        .perform(get("/samples").param("page", "1").param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(header().exists("X-Correlation-Id"))
        .andExpect(jsonPath("$.data._embedded.samples.length()").value(5))
        .andExpect(jsonPath("$.data._embedded.samples[0]._links.self.href").exists())
        .andExpect(jsonPath("$.data._links.self.href").exists())
        .andExpect(jsonPath("$.data._links.first.href", endsWith("page=0&size=5")))
        .andExpect(jsonPath("$.data._links.last.href").exists())
        .andExpect(jsonPath("$.data._links.prev.href", endsWith("page=0&size=5")))
        .andExpect(jsonPath("$.data._links.next.href", endsWith("page=2&size=5")))
        .andExpect(jsonPath("$.meta.pagination.page").value(1))
        .andExpect(jsonPath("$.meta.pagination.size").value(5))
        .andExpect(jsonPath("$.meta.pagination.totalElements").value(25))
        .andExpect(jsonPath("$.meta.pagination.totalPages").value(5));
  }

  @Test
  void listSamples_firstPage_omitsPrev() throws Exception {
    given(listSamplesUseCase.listSamples(0, 5))
        .willReturn(pageOf(0, 5, 25, List.of(item(1), item(2), item(3), item(4), item(5))));

    mockMvc
        .perform(get("/samples").param("page", "0").param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._links.self.href").exists())
        .andExpect(jsonPath("$.data._links.first.href").exists())
        .andExpect(jsonPath("$.data._links.last.href").exists())
        .andExpect(jsonPath("$.data._links.next.href").exists())
        .andExpect(jsonPath("$.data._links.prev").doesNotExist());
  }

  @Test
  void listSamples_lastPage_omitsNext() throws Exception {
    given(listSamplesUseCase.listSamples(4, 5))
        .willReturn(pageOf(4, 5, 25, List.of(item(21), item(22), item(23), item(24), item(25))));

    mockMvc
        .perform(get("/samples").param("page", "4").param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._links.prev.href").exists())
        .andExpect(jsonPath("$.data._links.next").doesNotExist());
  }

  @Test
  void listSamples_emptyResult_isOkWithNoNextOrPrev() throws Exception {
    given(listSamplesUseCase.listSamples(0, 5)).willReturn(pageOf(0, 5, 0, List.of()));

    mockMvc
        .perform(get("/samples").param("page", "0").param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._embedded.samples.length()").value(0))
        .andExpect(jsonPath("$.data._links.self.href").exists())
        .andExpect(jsonPath("$.data._links.next").doesNotExist())
        .andExpect(jsonPath("$.data._links.prev").doesNotExist());
  }

  @Test
  void listSamples_pageBeyondLastPage_isOkWithEmptyEmbeddedNotAnError() throws Exception {
    given(listSamplesUseCase.listSamples(9, 5)).willReturn(pageOf(9, 5, 25, List.of()));

    mockMvc
        .perform(get("/samples").param("page", "9").param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._embedded.samples.length()").value(0))
        .andExpect(jsonPath("$.data._links.self.href").exists())
        .andExpect(jsonPath("$.data._links.next").doesNotExist());
  }

  @Test
  void listSamples_sizeZero_isRejectedWith400ProblemDetailNotHalOrServerError() throws Exception {
    mockMvc
        .perform(get("/samples").param("size", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").exists())
        .andExpect(jsonPath("$.correlationId").exists())
        .andExpect(jsonPath("$._links").doesNotExist())
        .andExpect(jsonPath("$._embedded").doesNotExist());
  }

  @Test
  void listSamples_negativePage_isRejectedWith400ProblemDetail() throws Exception {
    mockMvc
        .perform(get("/samples").param("page", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").exists())
        .andExpect(jsonPath("$.correlationId").exists());
  }

  @Test
  void listSamples_sizeAboveDocumentedMaximum_isRejectedWith400() throws Exception {
    mockMvc
        .perform(get("/samples").param("size", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
  }
}
