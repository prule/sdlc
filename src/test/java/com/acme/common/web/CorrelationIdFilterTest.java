package com.acme.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class CorrelationIdFilterTest {

  private static final String UUID_PATTERN =
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @Test
  void doFilterInternal_adoptsAValidInboundUuid() throws Exception {
    String suppliedId = UUID.randomUUID().toString();
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn(suppliedId);

    filter.doFilter(request, response, chain);

    verify(response).setHeader(CorrelationIdFilter.HEADER, suppliedId);
    assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull(); // cleared after the request
  }

  @Test
  void doFilterInternal_replacesAnInvalidInboundValueWithAGeneratedUuid() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn("not-a-uuid");

    filter.doFilter(request, response, chain);

    verify(response)
        .setHeader(
            org.mockito.ArgumentMatchers.eq(CorrelationIdFilter.HEADER), argThatMatchesUuid());
  }

  @Test
  void doFilterInternal_generatesAUuidWhenNoHeaderIsSupplied() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn(null);

    filter.doFilter(request, response, chain);

    verify(response)
        .setHeader(
            org.mockito.ArgumentMatchers.eq(CorrelationIdFilter.HEADER), argThatMatchesUuid());
  }

  private static String argThatMatchesUuid() {
    return org.mockito.ArgumentMatchers.matches(UUID_PATTERN);
  }
}
