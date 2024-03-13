package com.datadog.trace.api.iast;

import com.datadog.trace.logger.Logger;
import com.datadog.trace.logger.LoggerFactory;

public interface IastModule {

  Logger LOG = LoggerFactory.getLogger(IastModule.class);

  default void onUnexpectedException(final String message, final Throwable error) {
    LOG.warn(message, error);
  }
}
