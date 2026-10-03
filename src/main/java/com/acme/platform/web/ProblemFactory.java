package com.acme.platform.web;

import java.net.URI;
import java.util.List;
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
   * As {@link #create(int)}, and when the failure is a {@code BAD_REQUEST} caused by named request
   * parameters, lists them in {@code errors} (field and a fixed reason; never the supplied value).
   */
  public ProblemDetail create(int resolvedStatus, List<InvalidParam> invalidParams) {
    ProblemKind kind = ProblemKind.classify(resolvedStatus);
    ProblemDetail problem = create(kind);
    if (kind == ProblemKind.BAD_REQUEST && !invalidParams.isEmpty()) {
      problem.setProperty("errors", List.copyOf(invalidParams));
    }
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

  /** One entry of a problem's {@code errors}: the offending parameter and why. */
  public record InvalidParam(String field, String message) {}
}
