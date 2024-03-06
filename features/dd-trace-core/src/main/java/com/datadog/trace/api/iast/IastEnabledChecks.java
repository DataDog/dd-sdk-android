package com.datadog.trace.api.iast;

import com.datadog.trace.logger.Logger;
import com.datadog.trace.logger.LoggerFactory;

import com.datadog.trace.api.Platform;

public abstract class IastEnabledChecks {

  private static final Logger LOGGER = LoggerFactory.getLogger(IastEnabledChecks.class);

  private IastEnabledChecks() {}

  public static boolean isMajorJavaVersionAtLeast(final String version) {
    try {
      return Platform.isJavaVersionAtLeast(Integer.parseInt(version));
    } catch (final Exception e) {
      LOGGER.error(
          "Error checking major java version {}, expect some call sites to be disabled",
          version,
          e);
      return false;
    }
  }
}