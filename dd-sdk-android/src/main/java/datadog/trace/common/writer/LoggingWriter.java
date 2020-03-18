package datadog.trace.common.writer;

import datadog.opentracing.DDSpan;
import java.util.List;

public class LoggingWriter implements Writer {

  @Override
  public void write(final List<DDSpan> trace) {
  }

  @Override
  public void incrementTraceCount() {
  }

  @Override
  public void close() {
  }

  @Override
  public void start() {
  }

  @Override
  public String toString() {
    return "LoggingWriter { }";
  }
}
