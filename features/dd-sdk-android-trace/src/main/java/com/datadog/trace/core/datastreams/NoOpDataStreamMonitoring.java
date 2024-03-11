package com.datadog.trace.core.datastreams;

import com.datadog.trace.api.experimental.DataStreamsContextCarrier;
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer;
import com.datadog.trace.bootstrap.instrumentation.api.PathwayContext;
import com.datadog.trace.bootstrap.instrumentation.api.StatsPoint;
import com.datadog.trace.core.propagation.HttpCodec;

import java.util.LinkedHashMap;

public class NoOpDataStreamMonitoring implements DataStreamsMonitoring {
  @Override
  public void setConsumeCheckpoint(String type, String source, DataStreamsContextCarrier carrier) {

  }

  @Override
  public void setProduceCheckpoint(String type, String target, DataStreamsContextCarrier carrier) {

  }

  @Override
  public void start() {

  }

  @Override
  public HttpCodec.Extractor extractor(HttpCodec.Extractor delegate) {
    return null;
  }

  @Override
  public DataStreamContextInjector injector() {
    return null;
  }

  @Override
  public void mergePathwayContextIntoSpan(AgentSpan span, DataStreamsContextCarrier carrier) {

  }

  @Override
  public void clear() {

  }

  @Override
  public void close() {

  }

  @Override
  public void trackBacklog(LinkedHashMap<String, String> sortedTags, long value) {

  }

  @Override
  public void setCheckpoint(AgentSpan span, LinkedHashMap<String, String> sortedTags, long defaultTimestamp, long payloadSizeBytes) {

  }

  @Override
  public PathwayContext newPathwayContext() {
    return AgentTracer.NoopPathwayContext.INSTANCE;
  }

  @Override
  public void add(StatsPoint statsPoint) {

  }

  @Override
  public int shouldSampleSchema(String topic) {
    return 0;
  }
}
