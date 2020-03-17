package datadog.opentracing;

import static io.opentracing.log.Fields.ERROR_OBJECT;
import static io.opentracing.log.Fields.MESSAGE;

import datadog.trace.api.DDTags;
import java.util.Map;

/** The default implementation of the LogHandler. */
public class DefaultLogHandler implements LogHandler {
  @Override
  public void log(Map<String, ?> fields, DDSpan span) {
    extractError(fields, span);
  }

  @Override
  public void log(long timestampMicroseconds, Map<String, ?> fields, DDSpan span) {
    extractError(fields, span);
  }

  @Override
  public void log(String event, DDSpan span) {
  }

  @Override
  public void log(long timestampMicroseconds, String event, DDSpan span) {
  }

  private void extractError(final Map<String, ?> map, DDSpan span) {
    if (map.get(ERROR_OBJECT) instanceof Throwable) {
      final Throwable error = (Throwable) map.get(ERROR_OBJECT);
      span.setErrorMeta(error);
    } else if (map.get(MESSAGE) instanceof String) {
      span.setTag(DDTags.ERROR_MSG, (String) map.get(MESSAGE));
    }
  }
}
