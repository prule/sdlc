package com.acme.platform.web;

import com.acme.platform.config.ClockConfig;
import com.acme.platform.config.SecurityConfig;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

/**
 * A {@code @WebMvcTest} slice pre-wired with the shared platform web infrastructure: security
 * (which also wires in {@link CorrelationIdFilter}), the global exception handler and its
 * supporting beans, and a fixed-clock-friendly {@link ClockConfig}. Every controller test in {@code
 * platform} and, later, {@code catalog} uses this instead of bare {@code @WebMvcTest} so the
 * read-only refusal, uniform problem form and correlation id are exercised consistently.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WebMvcTest
@Import({
  SecurityConfig.class,
  GlobalExceptionHandler.class,
  ProblemFactory.class,
  ProblemInstanceStrippingAdvice.class,
  ClockConfig.class,
  ResponseMetaFactory.class,
  PageLinkBuilder.class
})
public @interface PlatformWebTest {

  @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
  Class<?>[] controllers() default {};
}
