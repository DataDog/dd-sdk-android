package com.datadog.trace.api.iast;

import com.datadog.trace.logger.Logger;
import com.datadog.trace.logger.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public interface Taintable {

  Source $$DD$getSource();

  void $$DD$setSource(final Source source);

  default boolean $DD$isTainted() {
    return $$DD$getSource() != null;
  }

  /** Interface to isolate customer classloader from our classes */
  interface Source {
    byte getOrigin();

    String getName();

    String getValue();
  }

    class DebugLogger {
    private static final Logger LOGGER;

    static {
      try {
        LOGGER = LoggerFactory.getLogger("Taintable tainted objects");
        Class<?> levelCls = Class.forName("ch.qos.logback.classic.Level");
        Method setLevel = LOGGER.getClass().getMethod("setLevel", levelCls);
        Object debugLevel = levelCls.getField("DEBUG").get(null);
        setLevel.invoke(LOGGER, debugLevel);
      } catch (IllegalAccessException
          | NoSuchFieldException
          | ClassNotFoundException
          | NoSuchMethodException
          | InvocationTargetException e) {
        throw new RuntimeException(e);
      }
    }

    public static void logTaint(Taintable t) {
      String content;
      if (t.getClass().getName().startsWith("java.")) {
        content = t.toString();
      } else {
        content = "(value not shown)"; // toString() may trigger tainting
      }
      LOGGER.debug(
          "taint: {}[{}] {}",
          t.getClass().getSimpleName(),
          Integer.toHexString(System.identityHashCode(t)),
          content);
    }
  }
}