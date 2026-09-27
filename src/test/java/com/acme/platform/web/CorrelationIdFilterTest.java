package com.acme.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.DispatcherType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @Test
  void generatesANewIdWhenTheHeaderIsAbsent() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ping");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    String header = response.getHeader(CorrelationId.HEADER_NAME);
    assertThat(header).isNotBlank();
    assertThat(UUID.fromString(header)).isNotNull();
  }

  @Test
  void honoursAWellFormedInboundHeader() throws Exception {
    String inbound = "3f2b8c1e-8d4a-4c1e-9f0a-2b7d6e5c4a31";
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ping");
    request.addHeader(CorrelationId.HEADER_NAME, inbound);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getHeader(CorrelationId.HEADER_NAME)).isEqualTo(inbound);
  }

  @Test
  void replacesAMalformedInboundHeaderInsteadOfRejecting() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ping");
    request.addHeader(CorrelationId.HEADER_NAME, "not-a-uuid");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    String header = response.getHeader(CorrelationId.HEADER_NAME);
    assertThat(header).isNotEqualTo("not-a-uuid");
    assertThat(UUID.fromString(header)).isNotNull();
  }

  @Test
  void reusesTheStoredIdOnTheErrorDispatchInsteadOfGeneratingANewOne() throws Exception {
    String original = "3f2b8c1e-8d4a-4c1e-9f0a-2b7d6e5c4a31";
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ping");
    request.setAttribute(CorrelationId.ATTRIBUTE_NAME, original);
    request.setDispatcherType(DispatcherType.ERROR);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getHeader(CorrelationId.HEADER_NAME)).isEqualTo(original);
  }

  @Test
  void putsTheIdInTheMdcWhileTheChainRunsAndClearsItAfterwards() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ping");
    MockHttpServletResponse response = new MockHttpServletResponse();
    String[] mdcDuringChain = new String[1];

    filter.doFilter(
        request, response, (req, res) -> mdcDuringChain[0] = MDC.get(CorrelationId.ATTRIBUTE_NAME));

    assertThat(mdcDuringChain[0]).isNotBlank();
    assertThat(MDC.get(CorrelationId.ATTRIBUTE_NAME)).isNull();
  }
}
