package com.acme.platform.web;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Spring populates {@code ProblemDetail.instance} with the relative request URI while writing the
 * response, after any handler has returned, so nulling it earlier would not stick (design D3, Gate
 * 1). This advice nulls it in the last step before serialization; {@code non_null} Jackson
 * inclusion then omits the member entirely, and the relative value never leaks a client-visible
 * host or violates the schema's {@code format: uri}.
 */
@ControllerAdvice
public class ProblemInstanceStrippingAdvice implements ResponseBodyAdvice<Object> {

  // Handler methods that resolve to a ProblemDetail body (GlobalExceptionHandler's overridden
  // handleExceptionInternal, its catch-all, and ProblemErrorController) are declared to return
  // ResponseEntity<Object>, so the declared MethodParameter type here is erased to Object; the
  // actual body type is only known at write time. Always applying and checking the real runtime
  // instance in beforeBodyWrite is therefore the reliable check, not the declared return type.
  @Override
  public boolean supports(
      MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    return true;
  }

  @Override
  public @Nullable Object beforeBodyWrite(
      @Nullable Object body,
      MethodParameter returnType,
      MediaType selectedContentType,
      Class<? extends HttpMessageConverter<?>> selectedConverterType,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    if (body instanceof ProblemDetail problemDetail) {
      problemDetail.setInstance(null);
    }
    return body;
  }
}
