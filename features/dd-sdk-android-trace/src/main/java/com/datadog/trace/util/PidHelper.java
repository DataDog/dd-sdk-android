package com.datadog.trace.util;

import com.datadog.trace.api.Platform;
import com.datadog.trace.logger.Logger;
import com.datadog.trace.logger.LoggerFactory;

import com.datadog.android.trace.internal.compat.function.Supplier;

/** Get PID in reasonably cross-platform way. */
public final class PidHelper {
  private static final Logger log = LoggerFactory.getLogger(PidHelper.class);

  private static final String PID = findPid();

  private static final long PID_AS_LONG = parsePid();

  public static String getPid() {
    return PID;
  }

  /** Returns 0 if the PID is not a number. */
  public static long getPidAsLong() {
    return PID_AS_LONG;
  }

  private static String findPid() {
    String pid = "";
    if (Platform.isJavaVersionAtLeast(9)) {
      try {
        pid =
            Strings.trim(
                ((Supplier<String>)
                        Class.forName("com.datadog.trace.util.JDK9PidSupplier")
                            .getDeclaredConstructor()
                            .newInstance())
                    .get());
      } catch (Throwable e) {
        log.debug("JDK9PidSupplier not available", e);
      }
    }
    if (pid.isEmpty()) {
      try {
        // assumption: first part of runtime vmId is our process id
//        String vmId = ManagementFactory.getRuntimeMXBean().getName();
        String vmId ="";
        int pidEnd = vmId.indexOf('@');
        if (pidEnd > 0) {
          pid = vmId.substring(0, pidEnd).trim();
        }
      } catch (Throwable e) {
        log.debug("Process id not available", e);
      }
    }
    return pid;
  }

  private static long parsePid() {
    if (!PID.isEmpty()) {
      try {
        return Long.parseLong(PID);
      } catch (NumberFormatException e) {
        log.warn("Cannot parse PID {} as number. Default to 0", PID, e);
      }
    }
    return 0L;
  }
}
