package com.acme.testsupport;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/** Captures Logback log events written by a given class for the duration of a try-block. */
public final class LogCaptor implements AutoCloseable {

  private final Logger logger;
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  private LogCaptor(Class<?> loggedClass) {
    this.logger = (Logger) LoggerFactory.getLogger(loggedClass);
    appender.start();
    logger.addAppender(appender);
  }

  public static LogCaptor forClass(Class<?> loggedClass) {
    return new LogCaptor(loggedClass);
  }

  public List<ILoggingEvent> events() {
    return appender.list;
  }

  @Override
  public void close() {
    logger.detachAppender(appender);
    appender.stop();
  }
}
