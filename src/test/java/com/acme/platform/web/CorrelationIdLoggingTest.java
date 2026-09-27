package com.acme.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.acme.testsupport.LogCaptor;
import com.acme.testsupport.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The correlation id survives into the log: a 500 raised inside normal MVC handling logs it with
 * the stack trace, and a fault raised outside normal handling (a filter, rendered via the servlet
 * container's {@code /error} dispatch) carries the same id in the header, the body and the log
 * (uniform-responses "Correlation id on every response"). The {@code /error} case needs a real HTTP
 * client against a real server: MockMvc never performs the ERROR dispatch.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorrelationIdLoggingTest extends PostgresIntegrationTest {

  private static final String FILTER_FAULT_PATH = "/test-only/filter-throws";

  @TestConfiguration
  static class ThrowingFilterConfig {

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> throwingFilter() {
      OncePerRequestFilter filter =
          new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
              if (request.getRequestURI().endsWith(FILTER_FAULT_PATH)) {
                throw new IllegalStateException("filter-fault");
              }
              chain.doFilter(request, response);
            }
          };
      FilterRegistrationBean<OncePerRequestFilter> registration =
          new FilterRegistrationBean<>(filter);
      registration.addUrlPatterns(FILTER_FAULT_PATH);
      return registration;
    }
  }

  @org.springframework.beans.factory.annotation.Autowired private TestRestTemplate restTemplate;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void theFiveHundredPathLogsTheCorrelationIdAndTheStackTrace() throws Exception {
    String correlationId = UUID.randomUUID().toString();
    HttpHeaders headers = new HttpHeaders();
    headers.add(CorrelationId.HEADER_NAME, correlationId);

    try (LogCaptor logCaptor = LogCaptor.forClass(GlobalExceptionHandler.class)) {
      ResponseEntity<String> response =
          restTemplate.exchange(
              "/test-only/throws", HttpMethod.GET, new HttpEntity<>(headers), String.class);

      assertThat(response.getStatusCode().value()).isEqualTo(500);

      List<ILoggingEvent> events = logCaptor.events();
      assertThat(events)
          .anySatisfy(
              event -> {
                assertThat(event.getFormattedMessage()).contains(correlationId);
                assertThat(event.getThrowableProxy()).isNotNull();
              });
    }
  }

  @Test
  void aFailureRenderedViaErrorCarriesTheSameIdInHeaderBodyAndLog() throws Exception {
    String correlationId = UUID.randomUUID().toString();
    HttpHeaders headers = new HttpHeaders();
    headers.add(CorrelationId.HEADER_NAME, correlationId);

    try (LogCaptor logCaptor = LogCaptor.forClass(ProblemErrorController.class)) {
      ResponseEntity<String> response =
          restTemplate.exchange(
              FILTER_FAULT_PATH, HttpMethod.GET, new HttpEntity<>(headers), String.class);

      assertThat(response.getStatusCode().value()).isEqualTo(500);
      assertThat(response.getHeaders().getFirst(CorrelationId.HEADER_NAME))
          .isEqualTo(correlationId);

      JsonNode body = objectMapper.readTree(response.getBody());
      assertThat(body.get("correlationId").asText()).isEqualTo(correlationId);

      List<ILoggingEvent> events = logCaptor.events();
      assertThat(events)
          .anySatisfy(event -> assertThat(event.getFormattedMessage()).contains(correlationId));
    }
  }

  @Test
  void aRequestTheSecurityFirewallRejectsIsABadRequestProblemNotAServerFault() throws Exception {
    String correlationId = UUID.randomUUID().toString();
    HttpHeaders headers = new HttpHeaders();
    headers.add(CorrelationId.HEADER_NAME, correlationId);

    // StrictHttpFirewall rejects path parameters (';'): a client fault detected by the framework.
    ResponseEntity<String> response =
        restTemplate.exchange("/ping;x=y", HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getHeaders().getContentType()).hasToString("application/problem+json");
    assertThat(response.getHeaders().getFirst(CorrelationId.HEADER_NAME)).isEqualTo(correlationId);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.get("code").asText()).isEqualTo("BAD_REQUEST");
    assertThat(body.get("status").asInt()).isEqualTo(400);
    assertThat(body.get("correlationId").asText()).isEqualTo(correlationId);
  }
}
