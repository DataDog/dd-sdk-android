package com.datadog.trace.bootstrap.instrumentation.api;

import static java.util.Collections.emptyList;

import androidx.annotation.Nullable;

import com.datadog.trace.api.DDSpanId;
import com.datadog.trace.api.DDTraceId;
import com.datadog.trace.api.EndpointCheckpointer;
import com.datadog.trace.api.TraceConfig;
import com.datadog.trace.api.TracePropagationStyle;
import com.datadog.trace.api.experimental.DataStreamsContextCarrier;
import com.datadog.trace.api.gateway.Flow;
import com.datadog.trace.api.gateway.RequestContext;
import com.datadog.trace.api.gateway.RequestContextSlot;
import com.datadog.trace.api.internal.InternalTracer;
import com.datadog.trace.api.profiling.Timer;
import com.datadog.trace.api.sampling.PrioritySampling;
import com.datadog.trace.api.sampling.SamplingRule;
import com.datadog.trace.api.scopemanager.ScopeListener;
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan.Context;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.datadog.android.trace.internal.compat.function.Consumer;

public class AgentTracer {
  private static final String DEFAULT_INSTRUMENTATION_NAME = "datadog";

  // Not intended to be constructed.
  private AgentTracer() {}

  public interface TracerAPI
      extends com.datadog.trace.api.Tracer, InternalTracer, EndpointCheckpointer, ScopeStateAware {

    /**
     * Create and start a new span.
     *
     * @param instrumentationName The instrumentation creating the span.
     * @param spanName The span operation name.
     * @return The new started span.
     */
    AgentSpan startSpan(String instrumentationName, CharSequence spanName);

    /**
     * Create and start a new span with a given start time.
     *
     * @param instrumentationName The instrumentation creating the span.
     * @param spanName The span operation name.
     * @param startTimeMicros The span start time, in microseconds.
     * @return The new started span.
     */
    AgentSpan startSpan(String instrumentationName, CharSequence spanName, long startTimeMicros);

    /**
     * Create and start a new span with an explicit parent.
     *
     * @param instrumentationName The instrumentation creating the span.
     * @param spanName The span operation name.
     * @param parent The parent span context.
     * @return The new started span.
     */
    AgentSpan startSpan(
        String instrumentationName, CharSequence spanName, Context parent);

    /**
     * Create and start a new span with an explicit parent and a given start time.
     *
     * @param instrumentationName The instrumentation creating the span.
     * @param spanName The span operation name.
     * @param parent The parent span context.
     * @param startTimeMicros The span start time, in microseconds.
     * @return The new started span.
     */
    AgentSpan startSpan(
        String instrumentationName,
        CharSequence spanName,
        Context parent,
        long startTimeMicros);

    AgentScope activateSpan(AgentSpan span, ScopeSource source);

    AgentScope activateSpan(AgentSpan span, ScopeSource source, boolean isAsyncPropagating);

    AgentScope.Continuation captureSpan(AgentSpan span);

    void closePrevious(boolean finishSpan);

    AgentScope activateNext(AgentSpan span);

    @Nullable
    AgentSpan activeSpan();

    AgentScope activeScope();

    AgentPropagation propagate();

    AgentSpan noopSpan();

    /** Deprecated. Use {@link #buildSpan(String, CharSequence)} instead. */
    @Deprecated
    default SpanBuilder buildSpan(CharSequence spanName) {
      return buildSpan(DEFAULT_INSTRUMENTATION_NAME, spanName);
    }

    SpanBuilder buildSpan(String instrumentationName, CharSequence spanName);

    void close();

    /**
     * Attach a scope listener to the global scope manager
     *
     * @param listener listener to attach
     */
    void addScopeListener(ScopeListener listener);

    /**
     * Registers the checkpointer
     *
     * @param checkpointer
     */
    void registerCheckpointer(EndpointCheckpointer checkpointer);

    void registerTimer(Timer timer);

    Timer getTimer();

    String getTraceId(AgentSpan span);

    String getSpanId(AgentSpan span);

    TraceConfig captureTraceConfig();

    ProfilingContextIntegration getProfilingContext();

    AgentHistogram newHistogram(double relativeAccuracy, int maxNumBins);
  }

  public interface SpanBuilder {
    AgentSpan start();

    SpanBuilder asChildOf(Context toContext);

    SpanBuilder ignoreActiveSpan();

    SpanBuilder withTag(String key, String value);

    SpanBuilder withTag(String key, boolean value);

    SpanBuilder withTag(String key, Number value);

    SpanBuilder withTag(String tag, Object value);

    SpanBuilder withStartTimestamp(long microseconds);

    SpanBuilder withServiceName(String serviceName);

    SpanBuilder withResourceName(String resourceName);

    SpanBuilder withErrorFlag();

    SpanBuilder withSpanType(CharSequence spanType);

    <T> SpanBuilder withRequestContextData(RequestContextSlot slot, T data);

    SpanBuilder withLink(AgentSpanLink link);
  }

  public static final class NoopAgentSpan implements AgentSpan {
    public static final NoopAgentSpan INSTANCE = new NoopAgentSpan();

    private NoopAgentSpan() {}

    @Override
    public DDTraceId getTraceId() {
      return DDTraceId.ZERO;
    }

    @Override
    public long getSpanId() {
      return DDSpanId.ZERO;
    }

    @Override
    public AgentSpan setTag(final String key, final boolean value) {
      return this;
    }

    @Override
    public void setRequestBlockingAction(Flow.Action.RequestBlockingAction rba) {}

    @Override
    public Flow.Action.RequestBlockingAction getRequestBlockingAction() {
      return null;
    }

    @Override
    public AgentSpan setTag(final String tag, final Number value) {
      return this;
    }

    @Override
    public boolean isError() {
      return false;
    }

    @Override
    public AgentSpan setTag(final String key, final int value) {
      return this;
    }

    @Override
    public AgentSpan setTag(final String key, final long value) {
      return this;
    }

    @Override
    public AgentSpan setTag(final String key, final double value) {
      return this;
    }

    @Override
    public AgentSpan setTag(final String key, final Object value) {
      return this;
    }

    @Override
    public AgentSpan setMetric(final CharSequence key, final int value) {
      return this;
    }

    @Override
    public AgentSpan setMetric(final CharSequence key, final long value) {
      return this;
    }

    @Override
    public AgentSpan setMetric(final CharSequence key, final double value) {
      return this;
    }

    @Override
    public Object getTag(final String key) {
      return null;
    }

    @Override
    public long getStartTime() {
      return 0;
    }

    @Override
    public long getDurationNano() {
      return 0;
    }

    @Override
    public String getOperationName() {
      return null;
    }

    @Override
    public AgentSpan setOperationName(final CharSequence serviceName) {
      return this;
    }

    @Override
    public String getServiceName() {
      return null;
    }

    @Override
    public AgentSpan setServiceName(final String serviceName) {
      return this;
    }

    @Override
    public CharSequence getResourceName() {
      return null;
    }

    @Override
    public AgentSpan setResourceName(final CharSequence resourceName) {
      return this;
    }

    @Override
    public AgentSpan setResourceName(final CharSequence resourceName, byte priority) {
      return this;
    }

    @Override
    public boolean eligibleForDropping() {
      return true;
    }

    @Override
    public RequestContext getRequestContext() {
      return RequestContext.Noop.INSTANCE;
    }

    @Override
    public Integer forceSamplingDecision() {
      return null;
    }

    @Override
    public AgentSpan setSamplingPriority(int newPriority, int samplingMechanism) {
      return this;
    }

    @Override
    public Integer getSamplingPriority() {
      return (int) PrioritySampling.UNSET;
    }

    @Override
    public AgentSpan setSamplingPriority(final int newPriority) {
      return this;
    }

    @Override
    public String getSpanType() {
      return null;
    }

    @Override
    public AgentSpan setSpanType(final CharSequence type) {
      return this;
    }

    @Override
    public Map<String, Object> getTags() {
      return Collections.emptyMap();
    }

    @Override
    public AgentSpan setTag(final String key, final String value) {
      return this;
    }

    @Override
    public AgentSpan setTag(final String key, final CharSequence value) {
      return this;
    }

    @Override
    public AgentSpan setError(final boolean error) {
      return this;
    }

    @Override
    public AgentSpan setError(boolean error, byte priority) {
      return this;
    }

    @Override
    public AgentSpan setMeasured(boolean measured) {
      return this;
    }

    @Override
    public AgentSpan getRootSpan() {
      return this;
    }

    @Override
    public AgentSpan setErrorMessage(final String errorMessage) {
      return this;
    }

    @Override
    public AgentSpan addThrowable(final Throwable throwable) {
      return this;
    }

    @Override
    public AgentSpan addThrowable(Throwable throwable, byte errorPriority) {
      return this;
    }

    @Override
    public AgentSpan setHttpStatusCode(int statusCode) {
      return this;
    }

    @Override
    public short getHttpStatusCode() {
      return 0;
    }

    @Override
    public AgentSpan getLocalRootSpan() {
      return this;
    }

    @Override
    public boolean isSameTrace(final AgentSpan otherSpan) {
      // FIXME [API] AgentSpan or AgentSpan.Context should have a "getTraceId()" type method
      // Not sure if this is the best idea...
      return otherSpan == INSTANCE;
    }

    @Override
    public Context context() {
      return NoopContext.INSTANCE;
    }

    @Override
    public String getBaggageItem(final String key) {
      return null;
    }

    @Override
    public AgentSpan setBaggageItem(final String key, final String value) {
      return this;
    }

    @Override
    public void finish() {}

    @Override
    public void finish(final long finishMicros) {}

    @Override
    public void finishWithDuration(final long durationNanos) {}

    @Override
    public void beginEndToEnd() {}

    @Override
    public void finishWithEndToEnd() {}

    @Override
    public boolean phasedFinish() {
      return false;
    }

    @Override
    public void publish() {}

    @Override
    public String getSpanName() {
      return "";
    }

    @Override
    public void setSpanName(final CharSequence spanName) {}

    @Override
    public boolean hasResourceName() {
      return false;
    }

    @Override
    public byte getResourceNamePriority() {
      return Byte.MAX_VALUE;
    }

    @Override
    public TraceConfig traceConfig() {
      return NoopTraceConfig.INSTANCE;
    }

    @Override
    public void addLink(AgentSpanLink link) {}
  }

  public static final class NoopAgentScope implements AgentScope {
    public static final NoopAgentScope INSTANCE = new NoopAgentScope();

    private NoopAgentScope() {}

    @Override
    public AgentSpan span() {
      return NoopAgentSpan.INSTANCE;
    }

    @Override
    public byte source() {
      return 0;
    }

    @Override
    public void setAsyncPropagation(final boolean value) {}

    @Override
    public Continuation capture() {
      return NoopContinuation.INSTANCE;
    }

    @Override
    public Continuation captureConcurrent() {
      return NoopContinuation.INSTANCE;
    }

    @Override
    public void close() {}

    @Override
    public boolean isAsyncPropagating() {
      return false;
    }
  }

  static class NoopAgentPropagation implements AgentPropagation {
    static final NoopAgentPropagation INSTANCE = new NoopAgentPropagation();

    @Override
    public <C> void inject(final AgentSpan span, final C carrier, final Setter<C> setter) {}

    @Override
    public <C> void inject(final Context context, final C carrier, final Setter<C> setter) {}

    @Override
    public <C> void inject(
        AgentSpan span, C carrier, Setter<C> setter, TracePropagationStyle style) {}

    @Override
    public <C> void injectPathwayContext(
        AgentSpan span, C carrier, Setter<C> setter, LinkedHashMap<String, String> sortedTags) {}

    @Override
    public <C> void injectPathwayContext(
        AgentSpan span,
        C carrier,
        Setter<C> setter,
        LinkedHashMap<String, String> sortedTags,
        long defaultTimestamp,
        long payloadSizeBytes) {}

    @Override
    public <C> void injectPathwayContextWithoutSendingStats(
        AgentSpan span, C carrier, Setter<C> setter, LinkedHashMap<String, String> sortedTags) {}

    @Override
    public <C> Context.Extracted extract(final C carrier, final ContextVisitor<C> getter) {
      return NoopContext.INSTANCE;
    }
  }

  static class NoopContinuation implements AgentScope.Continuation {
    static final NoopContinuation INSTANCE = new NoopContinuation();

    @Override
    public AgentScope activate() {
      return NoopAgentScope.INSTANCE;
    }

    @Override
    public void cancel() {}

    @Override
    public AgentSpan getSpan() {
      return NoopAgentSpan.INSTANCE;
    }
  }

  public static final class NoopContext implements Context.Extracted {
    public static final NoopContext INSTANCE = new NoopContext();

    private NoopContext() {}

    @Override
    public DDTraceId getTraceId() {
      return DDTraceId.ZERO;
    }

    @Override
    public long getSpanId() {
      return DDSpanId.ZERO;
    }

    @Override
    public AgentTrace getTrace() {
      return NoopAgentTrace.INSTANCE;
    }

    @Override
    public int getSamplingPriority() {
      return PrioritySampling.UNSET;
    }

    @Override
    public Iterable<Map.Entry<String, String>> baggageItems() {
      return emptyList();
    }

    @Override
    public PathwayContext getPathwayContext() {
      return NoopPathwayContext.INSTANCE;
    }

    @Override
    public List<AgentSpanLink> getTerminatedContextLinks() {
      return emptyList();
    }

    @Override
    public String getForwarded() {
      return null;
    }

    @Override
    public String getFastlyClientIp() {
      return null;
    }

    @Override
    public String getCfConnectingIp() {
      return null;
    }

    @Override
    public String getCfConnectingIpv6() {
      return null;
    }

    @Override
    public String getXForwardedProto() {
      return null;
    }

    @Override
    public String getXForwardedHost() {
      return null;
    }

    @Override
    public String getXForwardedPort() {
      return null;
    }

    @Override
    public String getForwardedFor() {
      return null;
    }

    @Override
    public String getXForwarded() {
      return null;
    }

    @Override
    public String getXForwardedFor() {
      return null;
    }

    @Override
    public String getXClusterClientIp() {
      return null;
    }

    @Override
    public String getXRealIp() {
      return null;
    }

    @Override
    public String getXClientIp() {
      return null;
    }

    @Override
    public String getUserAgent() {
      return null;
    }

    @Override
    public String getTrueClientIp() {
      return null;
    }

    @Override
    public String getCustomIpHeader() {
      return null;
    }
  }

  public static class NoopAgentTrace implements AgentTrace {
    public static final NoopAgentTrace INSTANCE = new NoopAgentTrace();

    @Override
    public void registerContinuation(final AgentScope.Continuation continuation) {}

    @Override
    public void cancelContinuation(final AgentScope.Continuation continuation) {}
  }

  public static class NoopAgentDataStreamsMonitoring implements AgentDataStreamsMonitoring {
    public static final NoopAgentDataStreamsMonitoring INSTANCE =
        new NoopAgentDataStreamsMonitoring();

    @Override
    public void trackBacklog(LinkedHashMap<String, String> sortedTags, long value) {}

    @Override
    public void setCheckpoint(
        AgentSpan span,
        LinkedHashMap<String, String> sortedTags,
        long defaultTimestamp,
        long payloadSizeBytes) {}

    @Override
    public PathwayContext newPathwayContext() {
      return NoopPathwayContext.INSTANCE;
    }

    @Override
    public void add(StatsPoint statsPoint) {}

    @Override
    public int shouldSampleSchema(String topic) {
      return 0;
    }

    @Override
    public void setConsumeCheckpoint(
        String type, String source, DataStreamsContextCarrier carrier) {}

    @Override
    public void setProduceCheckpoint(
        String type, String target, DataStreamsContextCarrier carrier) {}
  }

  public static class NoopPathwayContext implements PathwayContext {
    public static final NoopPathwayContext INSTANCE = new NoopPathwayContext();

    @Override
    public boolean isStarted() {
      return false;
    }

    @Override
    public long getHash() {
      return 0L;
    }

    @Override
    public void setCheckpoint(
        LinkedHashMap<String, String> sortedTags,
        Consumer<StatsPoint> pointConsumer,
        long defaultTimestamp,
        long payloadSizeBytes) {}

    @Override
    public void setCheckpoint(
        LinkedHashMap<String, String> sortedTags,
        Consumer<StatsPoint> pointConsumer,
        long defaultTimestamp) {}

    @Override
    public void setCheckpoint(
        LinkedHashMap<String, String> sortedTags, Consumer<StatsPoint> pointConsumer) {}

    @Override
    public void saveStats(StatsPoint point) {}

    @Override
    public StatsPoint getSavedStats() {
      return null;
    }

    @Override
    public byte[] encode() {
      return null;
    }

    @Override
    public String strEncode() {
      return null;
    }
  }

  public static class NoopAgentHistogram implements AgentHistogram {
    public static final NoopAgentHistogram INSTANCE = new NoopAgentHistogram();

    @Override
    public double getCount() {
      return 0;
    }

    @Override
    public boolean isEmpty() {
      return true;
    }

    @Override
    public void accept(double value) {}

    @Override
    public void accept(double value, double count) {}

    @Override
    public double getValueAtQuantile(double quantile) {
      return 0;
    }

    @Override
    public double getMinValue() {
      return 0;
    }

    @Override
    public double getMaxValue() {
      return 0;
    }

    @Override
    public void clear() {}

    @Override
    public ByteBuffer serialize() {
      return null;
    }
  }

  /** TraceConfig when there is no tracer; this is not the same as a default config. */
  public static final class NoopTraceConfig implements TraceConfig {
    public static final NoopTraceConfig INSTANCE = new NoopTraceConfig();

    @Override
    public boolean isRuntimeMetricsEnabled() {
      return false;
    }

    @Override
    public boolean isLogsInjectionEnabled() {
      return false;
    }

    @Override
    public boolean isDataStreamsEnabled() {
      return false;
    }

    @Override
    public Map<String, String> getServiceMapping() {
      return Collections.emptyMap();
    }

    @Override
    public Map<String, String> getRequestHeaderTags() {
      return Collections.emptyMap();
    }

    @Override
    public Map<String, String> getResponseHeaderTags() {
      return Collections.emptyMap();
    }

    @Override
    public Map<String, String> getBaggageMapping() {
      return Collections.emptyMap();
    }

    @Override
    public Double getTraceSampleRate() {
      return null;
    }

    @Override
    public List<? extends SamplingRule.SpanSamplingRule> getSpanSamplingRules() {
      return Collections.emptyList();
    }

    @Override
    public List<? extends SamplingRule.TraceSamplingRule> getTraceSamplingRules() {
      return Collections.emptyList();
    }
  }
}
