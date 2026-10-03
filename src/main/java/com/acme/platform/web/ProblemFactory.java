package com.acme.platform.web;

import java.net.URI;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Builds the one {@link ProblemDetail} shape every failure uses, classifying the resolved status
 * into a {@link ProblemKind} and filling only fixed, safe fields. The framework or exception
 * message is never echoed (design D3); {@code instance} is left for {@link
 * ProblemInstanceStrippingAdvice} to strip before serialization.
 */
@Component
public class ProblemFactory {

  public ProblemDetail create(HttpStatusCode resolvedStatus) {
    ProblemKind kind = ProblemKind.classify(resolvedStatus.value());
    return create(kind);
  }

  public ProblemDetail create(int resolvedStatus) {
    return create(ProblemKind.classify(resolvedStatus));
  }

  /**
   * A {@code 400 BAD_REQUEST} naming the request parameter at fault (design D3). The name always
   * comes from code — a declared {@code @RequestParam}/{@code @PathVariable} name or {@code
   * InvalidRequestException.field()} — never from client input, and the value is never echoed.
   */
  public ProblemDetail createBadRequest(String parameterName) {
    ProblemDetail problem = create(ProblemKind.BAD_REQUEST);
    problem.setDetail("The request parameter '" + parameterName + "' is not valid.");
    return problem;
  }

  private ProblemDetail create(ProblemKind kind) {
    ProblemDetail problem = ProblemDetail.forStatus(kind.emittedStatus());
    problem.setType(URI.create(kind.type()));
    problem.setTitle(kind.title());
    problem.setDetail(kind.detail());
    problem.setProperty("code", kind.code());
    problem.setProperty("correlationId", CorrelationId.current().orElse(null));
    return problem;
  }
}
