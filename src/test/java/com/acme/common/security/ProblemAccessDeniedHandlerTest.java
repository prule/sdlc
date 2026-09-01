package com.acme.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

/**
 * Unit test of the filter-chain access-denied handler. The walking skeleton has only one (public)
 * endpoint, so there is no role-restricted business route through which to trigger a genuine 403
 * end to end; this exercises the handler directly, as the request/response contract it owns.
 */
@ExtendWith(MockitoExtension.class)
class ProblemAccessDeniedHandlerTest {

  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;

  @Test
  void handle_writesA403ProblemJsonBodyWithForbiddenCode() throws Exception {
    ProblemAccessDeniedHandler handler = new ProblemAccessDeniedHandler(new ObjectMapper());
    StringWriter body = new StringWriter();
    when(request.getRequestURI()).thenReturn("/api/v1/some-protected-thing");
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    handler.handle(request, response, new AccessDeniedException("denied"));

    org.mockito.Mockito.verify(response).setStatus(403);
    org.mockito.Mockito.verify(response).setContentType("application/problem+json");
    assertThat(body.toString()).contains("\"code\":\"FORBIDDEN\"");
    assertThat(body.toString()).contains("\"status\":403");
    assertThat(body.toString()).doesNotContain("Exception");
  }
}
