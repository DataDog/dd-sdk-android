package com.datadog.trace.core;

import static com.datadog.trace.api.ConfigDefaults.DEFAULT_ASYNC_PROPAGATING;
import static com.datadog.trace.api.DDTags.PROFILING_CONTEXT_ENGINE;
import static com.datadog.trace.util.AgentThreadFactory.AGENT_THREAD_GROUP;
import static com.datadog.trace.util.CollectionUtils.tryMakeImmutableMap;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.datadog.android.api.InternalLogger;
import com.datadog.trace.api.Config;
import com.datadog.trace.api.DDSpanId;
import com.datadog.trace.api.DDTraceId;
import com.datadog.trace.api.DynamicConfig;
import com.datadog.trace.api.EndpointCheckpointerHolder;
import com.datadog.trace.api.EndpointTracker;
import com.datadog.trace.api.IdGenerationStrategy;
import com.datadog.trace.api.InstrumenterConfig;
import com.datadog.trace.api.StatsDClient;
import com.datadog.trace.api.TracePropagationStyle;
import com.datadog.trace.api.gateway.RequestContext;
import com.datadog.trace.api.gateway.RequestContextSlot;
import com.datadog.trace.api.naming.SpanNaming;
import com.datadog.trace.api.profiling.Timer;
import com.datadog.trace.api.sampling.PrioritySampling;
import com.datadog.trace.api.scopemanager.ScopeListener;
import com.datadog.trace.api.time.SystemTimeSource;
import com.datadog.trace.api.time.TimeSource;
import com.datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import com.datadog.trace.bootstrap.instrumentation.api.AgentScope;
import com.datadog.trace.bootstrap.instrumentation.api.AgentScopeManager;
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpanLink;
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer;
import com.datadog.trace.bootstrap.instrumentation.api.PathwayContext;
import com.datadog.trace.bootstrap.instrumentation.api.ProfilingContextIntegration;
import com.datadog.trace.bootstrap.instrumentation.api.ScopeSource;
import com.datadog.trace.bootstrap.instrumentation.api.TagContext;
import com.datadog.trace.common.metrics.MetricsAggregator;
import com.datadog.trace.common.metrics.NoOpMetricsAggregator;
import com.datadog.trace.common.sampling.Sampler;
import com.datadog.trace.common.sampling.SpanSamplingRules;
import com.datadog.trace.common.sampling.TraceSamplingRules;
import com.datadog.trace.common.writer.NoOpWriter;
import com.datadog.trace.common.writer.Writer;
import com.datadog.trace.core.monitor.HealthMetrics;
import com.datadog.trace.core.propagation.CorePropagation;
import com.datadog.trace.core.propagation.ExtractedContext;
import com.datadog.trace.core.propagation.HttpCodec;
import com.datadog.trace.core.propagation.PropagationTags;
import com.datadog.trace.core.scopemanager.ContinuableScopeManager;
import com.datadog.trace.core.taginterceptor.RuleFlags;
import com.datadog.trace.core.taginterceptor.TagInterceptor;
import com.datadog.trace.logger.Logger;
import com.datadog.trace.logger.LoggerFactory;
import com.datadog.trace.monitor.NoOpRecording;
import com.datadog.trace.monitor.Recording;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Main entrypoint into the tracer implementation. In addition to implementing
 * com.datadog.trace.api.Tracer and TracerAPI, it coordinates many functions necessary creating,
 * reporting, and propagating traces
 */
public class CoreTracer implements AgentTracer.TracerAPI {
    @VisibleForTesting
    final Logger log;

    /**
     * Tracer start time in nanoseconds measured up to a millisecond accuracy
     */
    private final long startTimeNano;
    /**
     * Nanosecond ticks value at tracer start
     */
    private final long startNanoTicks;
    /**
     * How often should traced threads check clock ticks against the wall clock
     */
    private final long clockSyncPeriod;

    /**
     * If the tracer can create inferred services
     */
    private final boolean allowInferredServices;

    /**
     * Last time (in nanosecond ticks) the clock was checked for drift
     */
    private volatile long lastSyncTicks;
    /**
     * Nanosecond offset to counter clock drift
     */
    private volatile long counterDrift;

    private final PendingTraceBuffer pendingTraceBuffer;

    /**
     * Default service name if none provided on the trace or span
     */
    final String serviceName;
    /**
     * Writer is an charge of reporting traces and spans to the desired endpoint
     */
    final Writer writer;
    /**
     * Sampler defines the sampling policy in order to reduce the number of traces for instance
     */
    final Sampler initialSampler;
    /**
     * Scope manager is in charge of managing the scopes from which spans are created
     */
    final AgentScopeManager scopeManager;

    final MetricsAggregator metricsAggregator;

    /**
     * Initial static configuration associated with the tracer.
     */
    final Config initialConfig;
    /**
     * Maintains dynamic configuration associated with the tracer
     */
    private final DynamicConfig<ConfigSnapshot> dynamicConfig;
    /**
     * A set of tags that are added only to the application's root span
     */
    private final Map<String, ?> localRootSpanTags;
    /**
     * A set of tags that are added to every span
     */
    private final Map<String, ?> defaultSpanTags;

    /**
     * number of spans in a pending trace before they get flushed
     */
    private final int partialFlushMinSpans;

    private final StatsDClient statsDClient;
    private final HealthMetrics healthMetrics;
    private final Recording traceWriteTimer;
    private final IdGenerationStrategy idGenerationStrategy;
    private final PendingTrace.Factory pendingTraceFactory;
    private final EndpointCheckpointerHolder endpointCheckpointer;

    private final boolean disableSamplingMechanismValidation;
    private final TimeSource timeSource;
    private final ProfilingContextIntegration profilingContextIntegration;
    private boolean injectBaggageAsTags;

    private Timer timer = Timer.NoOp.INSTANCE;

    /**
     * JVM shutdown callback, keeping a reference to it to remove this if DDTracer gets destroyed
     * earlier
     */
    private final Thread shutdownCallback;

    /**
     * Span tag interceptors. This Map is only ever added to during initialization, so it doesn't need
     * to be concurrent.
     */
    private final TagInterceptor tagInterceptor;

    private final AgentPropagation propagation;

    private final boolean logs128bTraceIdEnabled;
    private final PropagationTags.Factory propagationTagsFactory;

    final InternalLogger internalLogger;

    @Override
    public ConfigSnapshot captureTraceConfig() {
        return dynamicConfig.captureTraceConfig();
    }


    PropagationTags.Factory getPropagationTagsFactory() {
        return propagationTagsFactory;
    }

    @Override
    public void onRootSpanFinished(AgentSpan root, EndpointTracker tracker) {
        endpointCheckpointer.onRootSpanFinished(root, tracker);
    }

    @Override
    public EndpointTracker onRootSpanStarted(AgentSpan root) {
        return endpointCheckpointer.onRootSpanStarted(root);
    }

    public static class CoreTracerBuilder {

        private Config config;
        private String serviceName;
        private Writer writer = new NoOpWriter();
        private IdGenerationStrategy idGenerationStrategy;
        private Sampler sampler;
        private HttpCodec.Extractor extractor;
        private HttpCodec.Injector injector;
        private AgentScopeManager scopeManager;
        private Map<String, ?> localRootSpanTags;
        private Map<String, ?> defaultSpanTags;
        private Map<String, String> serviceNameMappings;
        private Map<String, String> taggedHeaders;
        private Map<String, String> baggageMapping;
        private int partialFlushMinSpans;
        private TagInterceptor tagInterceptor;
        private boolean strictTraceWrites;
        private TimeSource timeSource;
        private ProfilingContextIntegration profilingContextIntegration =
                ProfilingContextIntegration.NoOp.INSTANCE;
        private InternalLogger internalLogger;
        private boolean injectBaggageAsTags;

        public CoreTracerBuilder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public CoreTracerBuilder writer(Writer writer) {
            this.writer = writer;
            return this;
        }

        public CoreTracerBuilder idGenerationStrategy(IdGenerationStrategy idGenerationStrategy) {
            this.idGenerationStrategy = idGenerationStrategy;
            return this;
        }

        public CoreTracerBuilder sampler(Sampler sampler) {
            this.sampler = sampler;
            return this;
        }

        public CoreTracerBuilder extractor(HttpCodec.Extractor extractor) {
            this.extractor = extractor;
            return this;
        }

        public CoreTracerBuilder injector(HttpCodec.Injector injector) {
            this.injector = injector;
            return this;
        }

        public CoreTracerBuilder scopeManager(AgentScopeManager scopeManager) {
            this.scopeManager = scopeManager;
            return this;
        }

        public CoreTracerBuilder localRootSpanTags(Map<String, ?> localRootSpanTags) {
            this.localRootSpanTags = tryMakeImmutableMap(localRootSpanTags);
            return this;
        }

        public CoreTracerBuilder defaultSpanTags(Map<String, ?> defaultSpanTags) {
            this.defaultSpanTags = tryMakeImmutableMap(defaultSpanTags);
            return this;
        }

        public CoreTracerBuilder serviceNameMappings(Map<String, String> serviceNameMappings) {
            this.serviceNameMappings = tryMakeImmutableMap(serviceNameMappings);
            return this;
        }

        public CoreTracerBuilder taggedHeaders(Map<String, String> taggedHeaders) {
            this.taggedHeaders = tryMakeImmutableMap(taggedHeaders);
            return this;
        }

        public CoreTracerBuilder baggageMapping(Map<String, String> baggageMapping) {
            this.baggageMapping = tryMakeImmutableMap(baggageMapping);
            return this;
        }

        public CoreTracerBuilder partialFlushMinSpans(int partialFlushMinSpans) {
            this.partialFlushMinSpans = partialFlushMinSpans;
            return this;
        }

        public CoreTracerBuilder tagInterceptor(TagInterceptor tagInterceptor) {
            this.tagInterceptor = tagInterceptor;
            return this;
        }

        public CoreTracerBuilder strictTraceWrites(boolean strictTraceWrites) {
            this.strictTraceWrites = strictTraceWrites;
            return this;
        }

        public CoreTracerBuilder timeSource(TimeSource timeSource) {
            this.timeSource = timeSource;
            return this;
        }


        public CoreTracerBuilder injectBaggageAsTags(boolean injectBaggageAsTags) {
            this.injectBaggageAsTags = injectBaggageAsTags;
            return this;
        }

        public CoreTracerBuilder(InternalLogger internalLogger) {
            // Apply the default values from config.
            config(Config.get());
            this.internalLogger = internalLogger;
        }

        public CoreTracerBuilder withProperties(final Properties properties) {
            return config(Config.get(properties));
        }

        public CoreTracerBuilder config(final Config config) {
            this.config = config;
            serviceName(config.getServiceName());
            // Explicitly skip setting writer to avoid allocating resources prematurely.
            sampler(Sampler.Builder.forConfig(config, null));
            // Explicitly skip setting scope manager because it depends on statsDClient
            localRootSpanTags(config.getLocalRootSpanTags());
            defaultSpanTags(config.getMergedSpanTags());
            serviceNameMappings(config.getServiceMapping());
            taggedHeaders(config.getRequestHeaderTags());
            baggageMapping(config.getBaggageMapping());
            partialFlushMinSpans(config.getPartialFlushMinSpans());
            strictTraceWrites(config.isTraceStrictWritesEnabled());
            injectBaggageAsTags(config.isInjectBaggageAsTagsEnabled());
            injector(HttpCodec.createInjector(
                    config,
                    config.getTracePropagationStylesToInject(),
                    invertMap(config.getBaggageMapping())));
            return this;
        }

        public CoreTracer build() {
            return new CoreTracer(
                    config,
                    serviceName,
                    writer,
                    idGenerationStrategy,
                    sampler,
                    injector,
                    extractor,
                    scopeManager,
                    localRootSpanTags,
                    defaultSpanTags,
                    serviceNameMappings,
                    taggedHeaders,
                    baggageMapping,
                    partialFlushMinSpans,
                    tagInterceptor,
                    strictTraceWrites,
                    timeSource,
                    profilingContextIntegration,
                    injectBaggageAsTags,
                    internalLogger);
        }
    }

    // These field names must be stable to ensure the builder api is stable.
    private CoreTracer(
            final Config config,
            final String serviceName,
            final Writer writer,
            final IdGenerationStrategy idGenerationStrategy,
            final Sampler sampler,
            final HttpCodec.Injector injector,
            final HttpCodec.Extractor extractor,
            final AgentScopeManager scopeManager,
            final Map<String, ?> localRootSpanTags,
            final Map<String, ?> defaultSpanTags,
            final Map<String, String> serviceNameMappings,
            final Map<String, String> taggedHeaders,
            final Map<String, String> baggageMapping,
            final int partialFlushMinSpans,
            final TagInterceptor tagInterceptor,
            final boolean strictTraceWrites,
            final TimeSource timeSource,
            final ProfilingContextIntegration profilingContextIntegration,
            final boolean injectBaggageAsTags,
            final InternalLogger internalLogger) {

        assert localRootSpanTags != null;
        assert defaultSpanTags != null;
        assert serviceNameMappings != null;
        assert taggedHeaders != null;
        assert baggageMapping != null;

        this.log = LoggerFactory.getLogger(CoreTracer.class.getSimpleName(), internalLogger);
        this.timeSource = timeSource == null ? SystemTimeSource.INSTANCE : timeSource;
        startTimeNano = this.timeSource.getCurrentTimeNanos();
        startNanoTicks = this.timeSource.getNanoTicks();
        clockSyncPeriod = Math.max(1_000_000L, SECONDS.toNanos(config.getClockSyncPeriod()));
        lastSyncTicks = startNanoTicks;

        endpointCheckpointer = EndpointCheckpointerHolder.create();
        this.serviceName = serviceName;

        this.initialConfig = config;
        this.initialSampler = sampler;

        // Get initial Trace Sampling Rules from config
        TraceSamplingRules traceSamplingRules =
                config.getTraceSamplingRules() == null
                        ? TraceSamplingRules.EMPTY
                        : TraceSamplingRules.deserialize(config.getTraceSamplingRules());
        // Get initial Span Sampling Rules from config
        String spanSamplingRulesJson = config.getSpanSamplingRules();
        String spanSamplingRulesFile = config.getSpanSamplingRulesFile();
        SpanSamplingRules spanSamplingRules = SpanSamplingRules.EMPTY;
        if (spanSamplingRulesJson != null) {
            spanSamplingRules = SpanSamplingRules.deserialize(spanSamplingRulesJson);
        } else if (spanSamplingRulesFile != null) {
            spanSamplingRules = SpanSamplingRules.deserializeFile(spanSamplingRulesFile);
        }

        this.dynamicConfig =
                DynamicConfig.create(ConfigSnapshot::new)
                        .setRuntimeMetricsEnabled(config.isRuntimeMetricsEnabled())
                        .setLogsInjectionEnabled(config.isLogsInjectionEnabled())
                        .setDataStreamsEnabled(config.isDataStreamsEnabled())
                        .setServiceMapping(serviceNameMappings)
                        .setHeaderTags(taggedHeaders)
                        .setBaggageMapping(baggageMapping)
                        .setTraceSampleRate(config.getTraceSampleRate())
                        .setSpanSamplingRules(spanSamplingRules.getRules())
                        .setTraceSamplingRules(traceSamplingRules.getRules())
                        .apply();

        this.logs128bTraceIdEnabled = InstrumenterConfig.get().isLogs128bTraceIdEnabled();
        this.defaultSpanTags = defaultSpanTags;
        this.partialFlushMinSpans = partialFlushMinSpans;
        this.idGenerationStrategy =
                null == idGenerationStrategy
                        ? Config.get().getIdGenerationStrategy()
                        : idGenerationStrategy;

        this.statsDClient = StatsDClient.NO_OP;

        this.traceWriteTimer = NoOpRecording.NO_OP;
        this.healthMetrics = HealthMetrics.NO_OP;
        if (scopeManager == null) {
            this.scopeManager =
                    new ContinuableScopeManager(
                            config.getScopeDepthLimit(),
                            config.isScopeStrictMode(),
                            config.isScopeInheritAsyncPropagation(),
                            profilingContextIntegration,
                            healthMetrics,
                            internalLogger);
        } else {
            this.scopeManager = scopeManager;
        }

        disableSamplingMechanismValidation = config.isSamplingMechanismValidationDisabled();

        this.writer = writer;
        pendingTraceBuffer =
                strictTraceWrites
                        ? PendingTraceBuffer.discarding(internalLogger)
                        : PendingTraceBuffer.delaying(
                        this.timeSource, config, healthMetrics);
        pendingTraceFactory =
                new PendingTrace.Factory(
                        this, pendingTraceBuffer, this.timeSource, strictTraceWrites, healthMetrics);
        pendingTraceBuffer.start();

        this.writer.start();
        metricsAggregator = NoOpMetricsAggregator.INSTANCE;
        // Schedule the metrics aggregator to begin reporting after a random delay of 1 to 10 seconds
        // (using milliseconds granularity.) This avoids a fleet of traced applications starting at the
        // same time from sending metrics in sync.

        // Create default extractor from config if not provided and decorate it with DSM extractor
        HttpCodec.Extractor builtExtractor =
                extractor == null ? HttpCodec.createExtractor(config, this::captureTraceConfig) : extractor;
        // Create all HTTP injectors plus the DSM one
        Map<TracePropagationStyle, HttpCodec.Injector> injectors =
                HttpCodec.allInjectorsFor(config, invertMap(baggageMapping));
        // Store all propagators to propagation
        this.propagation =
                new CorePropagation(builtExtractor, injector, injectors);
        this.tagInterceptor =
                null == tagInterceptor ? new TagInterceptor(new RuleFlags(config)) : tagInterceptor;

        shutdownCallback = new ShutdownHook(this);
        try {
            Runtime.getRuntime().addShutdownHook(shutdownCallback);
        } catch (final IllegalStateException ex) {
            // The JVM is already shutting down.
        }

        propagationTagsFactory = PropagationTags.factory(config);
        this.profilingContextIntegration = profilingContextIntegration;
        this.injectBaggageAsTags = injectBaggageAsTags;
        this.allowInferredServices = SpanNaming.instance().namingSchema().allowInferredServices();
        if (profilingContextIntegration != ProfilingContextIntegration.NoOp.INSTANCE) {
            Map<String, Object> tmp = new HashMap<>(localRootSpanTags);
            tmp.put(PROFILING_CONTEXT_ENGINE, profilingContextIntegration.name());
            this.localRootSpanTags = tryMakeImmutableMap(tmp);
        } else {
            this.localRootSpanTags = localRootSpanTags;
        }
        this.internalLogger = internalLogger;
    }

    @Override
    protected void finalize() {
        if (null != shutdownCallback) {
            try {
                shutdownCallback.run();
                Runtime.getRuntime().removeShutdownHook(shutdownCallback);
            } catch (final IllegalStateException e) {
                // Do nothing.  Already shutting down
            } catch (final Exception e) {
                log.error("Error while finalizing DDTracer.", e);
            }
        }
    }

    PendingTrace createTrace(DDTraceId id, ConfigSnapshot traceConfig) {
        return pendingTraceFactory.create(id, traceConfig);
    }

    /**
     * Timestamp in nanoseconds for the current {@code nanoTicks}.
     *
     * <p>Note: it is not possible to get 'real' nanosecond time. This method uses tracer start time
     * (with millisecond precision) as a reference and applies relative time with nanosecond precision
     * after that. This means time measured with same Tracer in different Spans is relatively correct
     * with nanosecond precision.
     *
     * @param nanoTicks as returned by {@link TimeSource#getNanoTicks()}
     * @return timestamp in nanoseconds
     */
    long getTimeWithNanoTicks(long nanoTicks) {
        long computedNanoTime = startTimeNano + Math.max(0, nanoTicks - startNanoTicks);
        if (nanoTicks - lastSyncTicks >= clockSyncPeriod) {
            long drift = computedNanoTime - timeSource.getCurrentTimeNanos();
            if (Math.abs(drift + counterDrift) >= 1_000_000L) { // allow up to 1ms of drift
                counterDrift = -MILLISECONDS.toNanos(NANOSECONDS.toMillis(drift));
            }
            lastSyncTicks = nanoTicks;
        }
        return computedNanoTime + counterDrift;
    }

    @Override
    public CoreSpanBuilder buildSpan(
            final String instrumentationName, final CharSequence operationName) {
        return new CoreSpanBuilder(instrumentationName, operationName, this);
    }

    @Override
    public AgentSpan startSpan(final String instrumentationName, final CharSequence spanName) {
        return buildSpan(instrumentationName, spanName).start();
    }

    @Override
    public AgentSpan startSpan(
            final String instrumentationName, final CharSequence spanName, final long startTimeMicros) {
        return buildSpan(instrumentationName, spanName).withStartTimestamp(startTimeMicros).start();
    }

    @Override
    public AgentSpan startSpan(
            String instrumentationName, final CharSequence spanName, final AgentSpan.Context parent) {
        return buildSpan(instrumentationName, spanName).ignoreActiveSpan().asChildOf(parent).start();
    }

    @Override
    public AgentSpan startSpan(
            final String instrumentationName,
            final CharSequence spanName,
            final AgentSpan.Context parent,
            final long startTimeMicros) {
        return buildSpan(instrumentationName, spanName)
                .ignoreActiveSpan()
                .asChildOf(parent)
                .withStartTimestamp(startTimeMicros)
                .start();
    }

    public AgentScope activateSpan(final AgentSpan span) {
        return scopeManager.activate(span, ScopeSource.INSTRUMENTATION, DEFAULT_ASYNC_PROPAGATING);
    }

    @Override
    public AgentScope activateSpan(final AgentSpan span, final ScopeSource source) {
        return scopeManager.activate(span, source);
    }

    @Override
    public AgentScope activateSpan(AgentSpan span, ScopeSource source, boolean isAsyncPropagating) {
        return scopeManager.activate(span, source, isAsyncPropagating);
    }
    public TagInterceptor getTagInterceptor() {
        return tagInterceptor;
    }

    public int getPartialFlushMinSpans() {
        return partialFlushMinSpans;
    }

    @Override
    public AgentSpan activeSpan() {
        return scopeManager.activeSpan();
    }

    @Override
    public AgentPropagation propagate() {
        return this.propagation;
    }

    /**
     * We use the sampler to know if the trace has to be reported/written. The sampler is called on
     * the first span (root span) of the trace. If the trace is marked as a sample, we report it.
     *
     * @param trace a list of the spans related to the same trace
     */
    void write(final List<DDSpan> trace) {
        List<DDSpan> writtenTrace = interceptCompleteTrace(trace);
        if (writtenTrace.isEmpty()) {
            return;
        }
        boolean forceKeep = metricsAggregator.publish(writtenTrace);

        PendingTrace pendingTrace = writtenTrace.get(0).context().getTrace();
        pendingTrace.setSamplingPriorityIfNecessary();

        DDSpan rootSpan = pendingTrace.getRootSpan();
        DDSpan spanToSample = rootSpan == null ? writtenTrace.get(0) : rootSpan;
        spanToSample.forceKeep(forceKeep);
        boolean published = forceKeep || pendingTrace.sample(spanToSample);
        if (published) {
            writer.write(writtenTrace);
        } else {
            // with span streaming this won't work - it needs to be changed
            // to track an effective sampling rate instead, however, tests
            // checking that a hard reference on a continuation prevents
            // reporting fail without this, so will need to be fixed first.
            writer.incrementDropCounts(writtenTrace.size());
        }
        if (null != rootSpan) {
            onRootSpanFinished(rootSpan, rootSpan.getEndpointTracker());

            // request context is propagated to contexts in child spans
            // Assume here that if present it will be so starting in the top span
            RequestContext requestContext = rootSpan.getRequestContext();
            if (requestContext != null) {
                try {
                    requestContext.close();
                } catch (IOException e) {
                    log.warn("Error closing request context data", e);
                }
            }
        }
    }

    private List<DDSpan> interceptCompleteTrace(List<DDSpan> trace) {
        return trace;
    }

    public String getTraceId() {
        return getTraceId(activeSpan());
    }

    public String getSpanId() {
        return getSpanId(activeSpan());
    }

    @Override
    public String getTraceId(AgentSpan span) {
        if (span != null && span.getTraceId() != null) {
            DDTraceId traceId = span.getTraceId();
            // Return padded hexadecimal string representation if 128-bit TraceId logging is enabled and
            // TraceId is a 128-bit ID, otherwise use the default numerical string representation.
            if (this.logs128bTraceIdEnabled && traceId.toHighOrderLong() != 0) {
                return traceId.toHexString();
            } else {
                return traceId.toString();
            }
        }
        return "0";
    }

    @Override
    public String getSpanId(AgentSpan span) {
        if (span != null) {
            return DDSpanId.toString(span.getSpanId());
        }
        return "0";
    }

    @Override
    public void addScopeListener(final ScopeListener listener) {
        if (scopeManager instanceof ContinuableScopeManager) {
            ((ContinuableScopeManager) scopeManager).addScopeListener(listener);
        }
    }

    @Override
    public void close() {
        pendingTraceBuffer.close();
        writer.close();
        statsDClient.close();
        metricsAggregator.close();
    }

    public void flush() {
        pendingTraceBuffer.flush();
        writer.flush();
    }

    Recording writeTimer() {
        return traceWriteTimer.start();
    }

    private static <K, V> Map<V, K> invertMap(Map<K, V> map) {
        Map<V, K> inverted = new HashMap<>(map.size());
        for (Map.Entry<K, V> entry : map.entrySet()) {
            inverted.put(entry.getValue(), entry.getKey());
        }
        return Collections.unmodifiableMap(inverted);
    }

    /**
     * Spans are built using this builder
     */
    public class CoreSpanBuilder implements AgentTracer.SpanBuilder {
        private final String instrumentationName;
        private final CharSequence operationName;
        private final CoreTracer tracer;

        // Builder attributes
        private Map<String, Object> tags;
        private long timestampMicro;
        private AgentSpan.Context parent;
        private String serviceName;
        private String resourceName;
        private boolean errorFlag;
        private CharSequence spanType;
        private boolean ignoreScope = false;
        private Object builderRequestContextDataAppSec;
        private Object builderRequestContextDataIast;
        private Object builderCiVisibilityContextData;
        private List<AgentSpanLink> links;
        private String origin;

        CoreSpanBuilder(
                final String instrumentationName, final CharSequence operationName, CoreTracer tracer) {
            this.instrumentationName = instrumentationName;
            this.operationName = operationName;
            this.tracer = tracer;
        }

        @Override
        public CoreSpanBuilder ignoreActiveSpan() {
            ignoreScope = true;
            return this;
        }

        private DDSpan buildSpan() {
            addTerminatedContextAsLinks();
            DDSpan span = DDSpan.create(
                    instrumentationName,
                    timestampMicro,
                    buildSpanContext(),
                    links,
                    tracer.internalLogger);
            if (span.isLocalRootSpan()) {
                EndpointTracker tracker = tracer.onRootSpanStarted(span);
                span.setEndpointTracker(tracker);
            }
            return span;
        }

        private void addTerminatedContextAsLinks() {
            if (this.parent instanceof TagContext) {
                List<AgentSpanLink> terminatedContextLinks =
                        ((TagContext) this.parent).getTerminatedContextLinks();
                if (!terminatedContextLinks.isEmpty()) {
                    if (this.links == null) {
                        this.links = new ArrayList<>();
                    }
                    this.links.addAll(terminatedContextLinks);
                }
            }
        }

        @Override
        public AgentSpan start() {
            return buildSpan();
        }

        @Override
        public CoreSpanBuilder withTag(final String tag, final Number number) {
            return withTag(tag, (Object) number);
        }

        @Override
        public CoreSpanBuilder withTag(final String tag, final String string) {
            return withTag(tag, (Object) (string == null || string.isEmpty() ? null : string));
        }

        @Override
        public CoreSpanBuilder withTag(final String tag, final boolean bool) {
            return withTag(tag, (Object) bool);
        }

        @Override
        public CoreSpanBuilder withStartTimestamp(final long timestampMicroseconds) {
            timestampMicro = timestampMicroseconds;
            return this;
        }

        @Override
        public CoreSpanBuilder withServiceName(final String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        @Override
        public CoreSpanBuilder withResourceName(final String resourceName) {
            this.resourceName = resourceName;
            return this;
        }

        @Override
        public CoreSpanBuilder withErrorFlag() {
            errorFlag = true;
            return this;
        }

        @Override
        public CoreSpanBuilder withSpanType(final CharSequence spanType) {
            this.spanType = spanType;
            return this;
        }

        @Override
        public CoreSpanBuilder asChildOf(final AgentSpan.Context spanContext) {
            // TODO we will start propagating stack trace hash and it will need to
            //  be extracted here if available
            parent = spanContext;
            return this;
        }

        public CoreSpanBuilder asChildOf(final AgentSpan agentSpan) {
            parent = agentSpan.context();
            return this;
        }

        @Override
        public CoreSpanBuilder withTag(final String tag, final Object value) {
            if (tag == null) {
                return this;
            }
            Map<String, Object> tagMap = tags;
            if (tagMap == null) {
                tags = tagMap = new LinkedHashMap<>(); // Insertion order is important
            }
            if (value == null) {
                tagMap.remove(tag);
            } else {
                tagMap.put(tag, value);
            }
            return this;
        }

        @Override
        public <T> AgentTracer.SpanBuilder withRequestContextData(RequestContextSlot slot, T data) {
            switch (slot) {
                case APPSEC:
                    builderRequestContextDataAppSec = data;
                    break;
                case CI_VISIBILITY:
                    builderCiVisibilityContextData = data;
                    break;
                case IAST:
                    builderRequestContextDataIast = data;
                    break;
            }
            return this;
        }

        @Override
        public AgentTracer.SpanBuilder withLink(AgentSpanLink link) {
            if (link != null) {
                if (this.links == null) {
                    this.links = new ArrayList<>();
                }
                this.links.add(link);
            }
            return this;
        }

        @NonNull
        @Override
        public AgentTracer.SpanBuilder withOrigin(@Nullable String origin) {
            this.origin = origin;
            return this;
        }

        /**
         * Build the SpanContext, if the actual span has a parent, the following attributes must be
         * propagated: - ServiceName - Baggage - Trace (a list of all spans related) - SpanType
         *
         * @return the context
         */
        private DDSpanContext buildSpanContext() {
            final DDTraceId traceId;
            final long spanId = idGenerationStrategy.generateSpanId();
            final long parentSpanId;
            final Map<String, String> baggage;
            final PendingTrace parentTrace;
            final int samplingPriority;
            final CharSequence origin;
            final Map<String, String> coreTags;
            final Map<String, ?> rootSpanTags;

            final DDSpanContext context;
            Object requestContextDataAppSec;
            Object requestContextDataIast;
            Object ciVisibilityContextData;
            final PathwayContext pathwayContext;
            final PropagationTags propagationTags;

            // FIXME [API] parentContext should be an interface implemented by ExtractedContext,
            // TagContext, DDSpanContext, AgentSpan.Context
            AgentSpan.Context parentContext = parent;
            if (parentContext == null && !ignoreScope) {
                // use the Scope as parent unless overridden or ignored.
                final AgentSpan activeSpan = scopeManager.activeSpan();
                if (activeSpan != null) {
                    parentContext = activeSpan.context();
                }
            }

            String parentServiceName = null;

            // Propagate internal trace.
            // Note: if we are not in the context of distributed tracing and we are starting the first
            // root span, parentContext will be null at this point.
            if (parentContext instanceof DDSpanContext) {
                final DDSpanContext ddsc = (DDSpanContext) parentContext;
                traceId = ddsc.getTraceId();
                parentSpanId = ddsc.getSpanId();
                baggage = ddsc.getBaggageItems();
                parentTrace = ddsc.getTrace();
                samplingPriority = PrioritySampling.UNSET;
                origin = null;
                coreTags = null;
                rootSpanTags = null;
                parentServiceName = ddsc.getServiceName();
                if (serviceName == null) {
                    serviceName = parentServiceName;
                }
                RequestContext requestContext = ((DDSpanContext) parentContext).getRequestContext();
                if (requestContext != null) {
                    requestContextDataAppSec = requestContext.getData(RequestContextSlot.APPSEC);
                    requestContextDataIast = requestContext.getData(RequestContextSlot.IAST);
                    ciVisibilityContextData = requestContext.getData(RequestContextSlot.CI_VISIBILITY);
                } else {
                    requestContextDataAppSec = null;
                    requestContextDataIast = null;
                    ciVisibilityContextData = null;
                }
                propagationTags = propagationTagsFactory.empty();
            } else {
                long endToEndStartTime;

                if (parentContext instanceof ExtractedContext) {
                    // Propagate external trace
                    final ExtractedContext extractedContext = (ExtractedContext) parentContext;
                    traceId = extractedContext.getTraceId();
                    parentSpanId = extractedContext.getSpanId();
                    samplingPriority = extractedContext.getSamplingPriority();
                    endToEndStartTime = extractedContext.getEndToEndStartTime();
                    propagationTags = extractedContext.getPropagationTags();
                } else if (parentContext != null) {
                    traceId =
                            parentContext.getTraceId() == DDTraceId.ZERO
                                    ? idGenerationStrategy.generateTraceId()
                                    : parentContext.getTraceId();
                    parentSpanId = parentContext.getSpanId();
                    samplingPriority = parentContext.getSamplingPriority();
                    endToEndStartTime = 0;
                    propagationTags = propagationTagsFactory.empty();
                } else {
                    // Start a new trace
                    traceId = idGenerationStrategy.generateTraceId();
                    parentSpanId = DDSpanId.ZERO;
                    samplingPriority = PrioritySampling.UNSET;
                    endToEndStartTime = 0;
                    propagationTags = propagationTagsFactory.empty();
                }

                ConfigSnapshot traceConfig;

                // Get header tags and set origin whether propagating or not.
                if (parentContext instanceof TagContext) {
                    TagContext tc = (TagContext) parentContext;
                    traceConfig = (ConfigSnapshot) tc.getTraceConfig();
                    coreTags = tc.getTags();
                    origin = tc.getOrigin();
                    baggage = tc.getBaggage();
                    requestContextDataAppSec = tc.getRequestContextDataAppSec();
                    requestContextDataIast = tc.getRequestContextDataIast();
                    ciVisibilityContextData = tc.getCiVisibilityContextData();
                } else {
                    origin = this.origin;
                    traceConfig = null;
                    coreTags = null;
                    baggage = null;
                    requestContextDataAppSec = null;
                    requestContextDataIast = null;
                    ciVisibilityContextData = null;
                }

                rootSpanTags = localRootSpanTags;

                parentTrace = createTrace(traceId, traceConfig);

                if (endToEndStartTime > 0) {
                    parentTrace.beginEndToEnd(endToEndStartTime);
                }
            }

            // Use parent pathwayContext if present and started
            pathwayContext =
                    parentContext != null
                            && parentContext.getPathwayContext() != null
                            && parentContext.getPathwayContext().isStarted()
                            ? parentContext.getPathwayContext()
                            : AgentTracer.NoopPathwayContext.INSTANCE;

            // when removing fake services the best upward service name to pick is the local root one
            // since a split by tag (i.e. servlet context) might have happened on it.
            if (!allowInferredServices) {
                final DDSpan rootSpan = parentTrace.getRootSpan();
                serviceName = rootSpan != null ? rootSpan.getServiceName() : null;
            }
            if (serviceName == null) {
                serviceName = CoreTracer.this.serviceName;
            }

            final CharSequence operationName =
                    this.operationName != null ? this.operationName : resourceName;

            final int tagsSize =
                    (null == tags ? 0 : tags.size())
                            + defaultSpanTags.size()
                            + (null == coreTags ? 0 : coreTags.size())
                            + (null == rootSpanTags ? 0 : rootSpanTags.size());

            if (builderRequestContextDataAppSec != null) {
                requestContextDataAppSec = builderRequestContextDataAppSec;
            }
            if (builderCiVisibilityContextData != null) {
                ciVisibilityContextData = builderCiVisibilityContextData;
            }
            if (builderRequestContextDataIast != null) {
                requestContextDataIast = builderRequestContextDataIast;
            }

            // some attributes are inherited from the parent
            context =
                    new DDSpanContext(
                            traceId,
                            spanId,
                            parentSpanId,
                            parentServiceName,
                            serviceName,
                            operationName,
                            resourceName,
                            samplingPriority,
                            origin,
                            baggage,
                            errorFlag,
                            spanType,
                            tagsSize,
                            parentTrace,
                            requestContextDataAppSec,
                            requestContextDataIast,
                            ciVisibilityContextData,
                            pathwayContext,
                            disableSamplingMechanismValidation,
                            propagationTags,
                            profilingContextIntegration,
                            injectBaggageAsTags);

            // By setting the tags on the context we apply decorators to any tags that have been set via
            // the builder. This is the order that the tags were added previously, but maybe the `tags`
            // set in the builder should come last, so that they override other tags.
            context.setAllTags(defaultSpanTags);
            context.setAllTags(tags);
            context.setAllTags(coreTags);
            context.setAllTags(rootSpanTags);
            return context;
        }
    }

    private static class ShutdownHook extends Thread {
        private final WeakReference<CoreTracer> reference;

        private ShutdownHook(final CoreTracer tracer) {
            super(AGENT_THREAD_GROUP, "dd-tracer-shutdown-hook");
            reference = new WeakReference<>(tracer);
        }

        @Override
        public void run() {
            final CoreTracer tracer = reference.get();
            if (tracer != null) {
                tracer.close();
            }
        }
    }

    protected class ConfigSnapshot extends DynamicConfig.Snapshot {
        final Sampler sampler;

        protected ConfigSnapshot(
                DynamicConfig<ConfigSnapshot>.Builder builder, ConfigSnapshot oldSnapshot) {
            super(builder, oldSnapshot);

            if (null == oldSnapshot) {
                sampler = CoreTracer.this.initialSampler;
            } else if (Objects.equals(getTraceSampleRate(), oldSnapshot.getTraceSampleRate())) {
                sampler = oldSnapshot.sampler;
            } else {
                sampler = Sampler.Builder.forConfig(CoreTracer.this.initialConfig, this);
            }
        }
    }
}
