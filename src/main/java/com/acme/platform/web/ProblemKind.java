package com.acme.platform.web;

import org.springframework.http.HttpStatus;

/**
 * The closed set of failure kinds every non-2xx response falls into, classified by the resolved
 * HTTP status (see design D3). Every other 4xx status collapses to {@link #BAD_REQUEST} and every
 * other 5xx status collapses to {@link #INTERNAL_ERROR}.
 */
enum ProblemKind {
  NOT_FOUND(
      HttpStatus.NOT_FOUND,
      "urn:problem-type:not-found",
      "Not Found",
      "NOT_FOUND",
      "The requested resource does not exist."),
  METHOD_NOT_ALLOWED(
      HttpStatus.METHOD_NOT_ALLOWED,
      "urn:problem-type:method-not-allowed",
      "Method Not Allowed",
      "METHOD_NOT_ALLOWED",
      "The requested method is not allowed for this resource."),
  NOT_ACCEPTABLE(
      HttpStatus.NOT_ACCEPTABLE,
      "urn:problem-type:not-acceptable",
      "Not Acceptable",
      "NOT_ACCEPTABLE",
      "None of the representations this client can accept are available."),
  UNSUPPORTED_MEDIA_TYPE(
      HttpStatus.UNSUPPORTED_MEDIA_TYPE,
      "urn:problem-type:unsupported-media-type",
      "Unsupported Media Type",
      "UNSUPPORTED_MEDIA_TYPE",
      "The request's media type is not supported."),
  BAD_REQUEST(
      HttpStatus.BAD_REQUEST,
      "urn:problem-type:bad-request",
      "Bad Request",
      "BAD_REQUEST",
      "The request could not be understood."),
  INTERNAL_ERROR(
      HttpStatus.INTERNAL_SERVER_ERROR,
      "urn:problem-type:internal-error",
      "Internal Server Error",
      "INTERNAL_ERROR",
      "An unexpected error occurred.");

  private final HttpStatus emittedStatus;
  private final String type;
  private final String title;
  private final String code;
  private final String detail;

  ProblemKind(HttpStatus emittedStatus, String type, String title, String code, String detail) {
    this.emittedStatus = emittedStatus;
    this.type = type;
    this.title = title;
    this.code = code;
    this.detail = detail;
  }

  /** Classifies a resolved status into exactly one kind (design D3). */
  static ProblemKind classify(int resolvedStatus) {
    return switch (resolvedStatus) {
      case 404 -> NOT_FOUND;
      case 405 -> METHOD_NOT_ALLOWED;
      case 406 -> NOT_ACCEPTABLE;
      case 415 -> UNSUPPORTED_MEDIA_TYPE;
      default -> resolvedStatus >= 500 ? INTERNAL_ERROR : BAD_REQUEST;
    };
  }

  HttpStatus emittedStatus() {
    return emittedStatus;
  }

  String type() {
    return type;
  }

  String title() {
    return title;
  }

  String code() {
    return code;
  }

  String detail() {
    return detail;
  }
}
