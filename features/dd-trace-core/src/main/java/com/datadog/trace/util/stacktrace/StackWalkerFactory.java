package com.datadog.trace.util.stacktrace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.datadog.trace.api.Platform;

public class StackWalkerFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(StackWalkerFactory.class);

  public static final StackWalker INSTANCE;

  static {
    Stream<StackWalker> stream = Stream.of(hotspot(), jdk9()).map(Supplier::get);
    INSTANCE =
        stream
            .filter(Objects::nonNull)
            .filter(StackWalker::isEnabled)
            .findFirst()
            .orElseGet(defaultStackWalker());
  }

  private static Supplier<StackWalker> defaultStackWalker() {
    return DefaultStackWalker::new;
  }

//  private static Supplier<StackWalker> hotspot() {
//    return () -> {
//      if (!Platform.isJavaVersion(8)) {
//        return null;
//      }
//      return new HotSpotStackWalker();
//    };
//  }

  private static Supplier<StackWalker> hotspot() {
    return () -> {
      return null;
    };
  }

  private static Supplier<StackWalker> jdk9() {
    return () -> {
      if (!Platform.isJavaVersionAtLeast(9)) {
        return null;
      }
      try {
        return (StackWalker)
            Class.forName("com.datadog.trace.util.stacktrace.JDK9StackWalker")
                .getDeclaredConstructor()
                .newInstance();
      } catch (Throwable e) {
        LOGGER.warn("JDK9StackWalker not available", e);
        return null;
      }
    };
  }
}
