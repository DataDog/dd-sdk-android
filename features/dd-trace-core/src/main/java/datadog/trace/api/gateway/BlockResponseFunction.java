package datadog.trace.api.gateway;

import java.util.Map;

import datadog.trace.appsec.api.blocking.BlockingContentType;
import datadog.trace.api.internal.TraceSegment;

public interface BlockResponseFunction {
  /**
   * Commits blocking response.
   *
   * <p>It's responsible for calling {@link TraceSegment#effectivelyBlocked()} before the span is
   * finished.
   *
   * @return true unless blocking could not be attempted
   */
  boolean tryCommitBlockingResponse(
      TraceSegment segment,
      int statusCode,
      BlockingContentType templateType,
      Map<String, String> extraHeaders);
}
