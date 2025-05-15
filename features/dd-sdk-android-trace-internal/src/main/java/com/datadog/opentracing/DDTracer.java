/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentracing;

import android.os.StrictMode;

import com.datadog.android.api.InternalLogger;
import com.datadog.opentracing.decorators.AbstractDecorator;
import com.datadog.opentracing.decorators.DDDecoratorsFactory;
import com.datadog.opentracing.jfr.DDNoopScopeEventFactory;
import com.datadog.opentracing.jfr.DDScopeEventFactory;
import com.datadog.opentracing.propagation.ExtractedContext;
import com.datadog.opentracing.propagation.HttpCodec;
import com.datadog.opentracing.propagation.TagContext;
import com.datadog.opentracing.scopemanager.ContextualScopeManager;
import com.datadog.opentracing.scopemanager.ScopeContext;
import com.datadog.legacy.trace.api.Config;
import com.datadog.legacy.trace.api.Tracer;
import com.datadog.legacy.trace.api.interceptor.MutableSpan;
import com.datadog.legacy.trace.api.interceptor.TraceInterceptor;
import com.datadog.legacy.trace.api.sampling.PrioritySampling;
import com.datadog.legacy.trace.common.sampling.PrioritySampler;
import com.datadog.legacy.trace.common.sampling.Sampler;
import com.datadog.legacy.trace.common.writer.LoggingWriter;
import com.datadog.legacy.trace.common.writer.Writer;
import com.datadog.legacy.trace.context.ScopeListener;
import com.datadog.trace.api.IdGenerationStrategy;

import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapExtract;
import io.opentracing.propagation.TextMapInject;
import io.opentracing.tag.Tag;

import java.io.Closeable;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * DDTracer makes it easy to send traces and span to DD using the OpenTracing API.
 */
public class DDTracer implements io.opentracing.Tracer, Closeable, Tracer {
    // UINT128 max value
    public static final BigInteger TRACE_ID_128_BITS_MAX =
            BigInteger.valueOf(2).pow(128).subtract(BigInteger.ONE);

    // UINT64 max value
    public static final BigInteger TRACE_ID_64_BITS_MAX =
            BigInteger.valueOf(2).pow(64).subtract(BigInteger.ONE);

    public static final BigInteger TRACE_ID_MIN = BigInteger.ZERO;

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
    final Sampler sampler;
    /**
     * Scope manager is in charge of managing the scopes from which spans are created
     */
    final ScopeManager scopeManager;

    /**
     * A set of tags that are added only to the application's root span
     */
    private final Map<String, String> localRootSpanTags;
    /**
     * A set of tags that are added to every span
     */
    private final Map<String, String> defaultSpanTags;
    /**
     * A configured mapping of service names to update with new values
     */
    private final Map<String, String> serviceNameMappings;

    /**
     * number of spans in a pending trace before they get flushed
     */
    private final int partialFlushMinSpans;

    /**
     * JVM shutdown callback, keeping a reference to it to remove this if DDTracer gets destroyed
     * earlier
     */
    private final Thread shutdownCallback;

    /**
     * Span context decorators
     */
    private final Map<String, List<AbstractDecorator>> spanContextDecorators =
            new ConcurrentHashMap<>();

    private final SortedSet<TraceInterceptor> interceptors =
            new ConcurrentSkipListSet<>(
                    new Comparator<TraceInterceptor>() {
                        @Override
                        public int compare(final TraceInterceptor o1, final TraceInterceptor o2) {
                            return Integer.compare(o1.priority(), o2.priority());
                        }
                    });

    private final HttpCodec.Injector injector;
    private final HttpCodec.Extractor extractor;

    private final IdGenerationStrategy idGenerationStrategy =
            IdGenerationStrategy.fromName("SECURE_RANDOM", true);

    // On Android, the same zygote is reused for every single application,
    // meaning that the ThreadLocalRandom reuses the same exact state,
    // resulting in conflicting TraceIds.
    // To avoid this we will use a SecureRandom instance here to generate the trace id.

    private final Random random;

    protected DDTracer(final Config config, final Writer writer, final Random random) {
        this(
                config.getServiceName(),
                writer,
                Sampler.Builder.forConfig(config),
                HttpCodec.createInjector(config),
                HttpCodec.createExtractor(config, config.getHeaderTags()),
                new ContextualScopeManager(Config.get().getScopeDepthLimit(), createScopeEventFactory()),
                random,
                config.getLocalRootSpanTags(),
                config.getMergedSpanTags(),
                config.getServiceMapping(),
                config.getHeaderTags(),
                config.getPartialFlushMinSpans());
    }


    // These field names must be stable to ensure the builder api is stable.
    private DDTracer(
            final String serviceName,
            final Writer writer,
            final Sampler sampler,
            final HttpCodec.Injector injector,
            final HttpCodec.Extractor extractor,
            final ScopeManager scopeManager,
            final Random random,
            final Map<String, String> localRootSpanTags,
            final Map<String, String> defaultSpanTags,
            final Map<String, String> serviceNameMappings,
            final Map<String, String> taggedHeaders,
            final int partialFlushMinSpans) {

        assert localRootSpanTags != null;
        assert defaultSpanTags != null;
        assert serviceNameMappings != null;
        assert taggedHeaders != null;

        this.random = random;
        this.serviceName = serviceName;
        if (writer == null) {
            this.writer = new LoggingWriter();
        } else {
            this.writer = writer;
        }
        this.sampler = sampler;
        this.injector = injector;
        this.extractor = extractor;
        this.scopeManager = scopeManager;
        this.localRootSpanTags = localRootSpanTags;
        this.defaultSpanTags = defaultSpanTags;
        this.serviceNameMappings = serviceNameMappings;
        this.partialFlushMinSpans = partialFlushMinSpans;

        this.writer.start();

        shutdownCallback = new ShutdownHook(this);
        try {
            Runtime.getRuntime().addShutdownHook(shutdownCallback);
        } catch (final IllegalStateException ex) {
            // The JVM is already shutting down.
        }

        final List<AbstractDecorator> decorators = DDDecoratorsFactory.createBuiltinDecorators();
        for (final AbstractDecorator decorator : decorators) {
            addDecorator(decorator);
        }

        registerClassLoader(ClassLoader.getSystemClassLoader());

        // Ensure that PendingTrace.SPAN_CLEANER is initialized in this thread:
        // FIXME: add test to verify the span cleaner thread is started with this call.
        PendingTrace.initialize();
    }

    @Override
    public void finalize() {
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownCallback);
            shutdownCallback.run();
        } catch (final Exception e) {
        }
    }

    /**
     * Returns the list of span context decorators
     *
     * @return the list of span context decorators
     */
    public List<AbstractDecorator> getSpanContextDecorators(final String tag) {
        return spanContextDecorators.get(tag);
    }

    /**
     * Add a new decorator in the list ({@link AbstractDecorator})
     *
     * @param decorator The decorator in the list
     */
    public void addDecorator(final AbstractDecorator decorator) {

        List<AbstractDecorator> list = spanContextDecorators.get(decorator.getMatchingTag());
        if (list == null) {
            list = new ArrayList<>();
        }
        list.add(decorator);

        spanContextDecorators.put(decorator.getMatchingTag(), list);
    }

    @Deprecated
    public void addScopeContext(final ScopeContext context) {
        if (scopeManager instanceof ContextualScopeManager) {
            ((ContextualScopeManager) scopeManager).addScopeContext(context);
        }
    }

    /**
     * If an application is using a non-system classloader, that classloader should be registered
     * here. Due to the way Spring Boot structures its' executable jar, this might log some warnings.
     *
     * @param classLoader to register.
     */
    public void registerClassLoader(final ClassLoader classLoader) {
        try {
            StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
            ServiceLoader<TraceInterceptor> serviceLoader = ServiceLoader.load(TraceInterceptor.class, classLoader);
            for (final TraceInterceptor interceptor : serviceLoader) {
                addTraceInterceptor(interceptor);
            }
            StrictMode.setThreadPolicy(oldPolicy);
        } catch (final ServiceConfigurationError e) {
        }
    }

    @Override
    public ScopeManager scopeManager() {
        return scopeManager;
    }

    @Override
    public Span activeSpan() {
        return scopeManager.activeSpan();
    }

    @Override
    public Scope activateSpan(final Span span) {
        return scopeManager.activate(span);
    }

    @Override
    public SpanBuilder buildSpan(final String operationName) {
        return new DDSpanBuilder(operationName, scopeManager);
    }

    @Override
    public <T> void inject(final SpanContext spanContext, final Format<T> format, final T carrier) {
        if (carrier instanceof TextMapInject) {
            final DDSpanContext ddSpanContext = (DDSpanContext) spanContext;

            final DDSpan rootSpan = ddSpanContext.getTrace().getRootSpan();
            setSamplingPriorityIfNecessary(rootSpan);

            injector.inject(ddSpanContext, (TextMapInject) carrier);
        } else {
        }
    }

    @Override
    public <T> SpanContext extract(final Format<T> format, final T carrier) {
        if (carrier instanceof TextMapExtract) {
            return extractor.extract((TextMapExtract) carrier);
        } else {
            return null;
        }
    }

    /**
     * We use the sampler to know if the trace has to be reported/written. The sampler is called on
     * the first span (root span) of the trace. If the trace is marked as a sample, we report it.
     *
     * @param trace a list of the spans related to the same trace
     */
    void write(final Collection<DDSpan> trace) {
        if (trace.isEmpty()) {
            return;
        }
        final ArrayList<DDSpan> writtenTrace;
        if (interceptors.isEmpty()) {
            writtenTrace = new ArrayList<>(trace);
        } else {
            Collection<? extends MutableSpan> interceptedTrace = new ArrayList<>(trace);
            for (final TraceInterceptor interceptor : interceptors) {
                interceptedTrace = interceptor.onTraceComplete(interceptedTrace);
            }
            writtenTrace = new ArrayList<>(interceptedTrace.size());
            for (final MutableSpan span : interceptedTrace) {
                if (span instanceof DDSpan) {
                    writtenTrace.add((DDSpan) span);
                }
            }
        }
        incrementTraceCount();

        if (!writtenTrace.isEmpty()) {
            final DDSpan rootSpan = (DDSpan) writtenTrace.get(0).getLocalRootSpan();
            setSamplingPriorityIfNecessary(rootSpan);

            final DDSpan spanToSample = rootSpan == null ? writtenTrace.get(0) : rootSpan;
            if (sampler.sample(spanToSample)) {
                writer.write(writtenTrace);
            }
        }
    }

    void setSamplingPriorityIfNecessary(final DDSpan rootSpan) {
        // There's a race where multiple threads can see PrioritySampling.UNSET here
        // This check skips potential complex sampling priority logic when we know its redundant
        // Locks inside DDSpanContext ensure the correct behavior in the race case

        if (sampler instanceof PrioritySampler
                && rootSpan != null
                && rootSpan.context().getSamplingPriority() == PrioritySampling.UNSET) {

            ((PrioritySampler) sampler).setSamplingPriority(rootSpan);
        }
    }

    /**
     * Increment the reported trace count, but do not write a trace.
     */
    void incrementTraceCount() {
        writer.incrementTraceCount();
    }

    @Override
    public String getTraceId() {
        final Span activeSpan = activeSpan();
        if (activeSpan instanceof DDSpan) {
            return ((DDSpan) activeSpan).getTraceId().toString();
        }
        return "0";
    }

    @Override
    public String getSpanId() {
        final Span activeSpan = activeSpan();
        if (activeSpan instanceof DDSpan) {
            return ((DDSpan) activeSpan).getSpanId().toString();
        }
        return "0";
    }

    @Override
    public boolean addTraceInterceptor(final TraceInterceptor interceptor) {
        return interceptors.add(interceptor);
    }

    @Override
    public void addScopeListener(final ScopeListener listener) {
        if (scopeManager instanceof ContextualScopeManager) {
            ((ContextualScopeManager) scopeManager).addScopeListener(listener);
        }
    }

    @Override
    public void close() {
        PendingTrace.close();
        writer.close();
    }

    @Override
    public String toString() {
        return "DDTracer-"
                + Integer.toHexString(hashCode())
                + "{ serviceName="
                + serviceName
                + ", writer="
                + writer
                + ", sampler="
                + sampler
                + ", defaultSpanTags="
                + defaultSpanTags
                + '}';
    }

    @Deprecated
    private static Map<String, String> customRuntimeTags(
            final String runtimeId, final Map<String, String> applicationRootSpanTags) {
        final Map<String, String> runtimeTags = new HashMap<>(applicationRootSpanTags);
        runtimeTags.put(Config.RUNTIME_ID_TAG, runtimeId);
        return Collections.unmodifiableMap(runtimeTags);
    }

    private static DDScopeEventFactory createScopeEventFactory() {
        try {
            return (DDScopeEventFactory)
                    Class.forName("com.datadog.opentracing.jfr.openjdk.ScopeEventFactory").newInstance();
        } catch (final ClassFormatError | ReflectiveOperationException | NoClassDefFoundError e) {
        }
        return new DDNoopScopeEventFactory();
    }

    /**
     * Spans are built using this builder
     */
    public class DDSpanBuilder implements SpanBuilder {
        private final ScopeManager scopeManager;

        /**
         * Each span must have an operationName according to the opentracing specification
         */
        private final String operationName;

        // Builder attributes
        private final Map<String, Object> tags = new LinkedHashMap<String, Object>(defaultSpanTags);
        private long timestampMicro;
        private SpanContext parent;
        private String serviceName;
        private String resourceName;
        private String origin;
        private boolean errorFlag;
        private String spanType;
        private boolean ignoreScope = false;
        private LogHandler logHandler = new DefaultLogHandler();
        private InternalLogger internalLogger = InternalLogger.Companion.getUNBOUND();

        public DDSpanBuilder(final String operationName, final ScopeManager scopeManager) {
            this.operationName = operationName;
            this.scopeManager = scopeManager;
        }

        @Override
        public SpanBuilder ignoreActiveSpan() {
            ignoreScope = true;
            return this;
        }

        private Span startSpan() {
            return new DDSpan(timestampMicro, buildSpanContext(), logHandler, internalLogger);
        }

        @Override
        public Scope startActive(final boolean finishSpanOnClose) {
            final Span span = startSpan();
            final Scope scope = scopeManager.activate(span, finishSpanOnClose);
            return scope;
        }

        @Override
        @Deprecated
        public Span startManual() {
            return start();
        }

        @Override
        public Span start() {
            final Span span = startSpan();
            return span;
        }

        @Override
        public DDSpanBuilder withTag(final String tag, final Number number) {
            return withTag(tag, (Object) number);
        }

        @Override
        public DDSpanBuilder withTag(final String tag, final String string) {
            return withTag(tag, (Object) string);
        }

        @Override
        public DDSpanBuilder withTag(final String tag, final boolean bool) {
            return withTag(tag, (Object) bool);
        }

        @Override
        public <T> SpanBuilder withTag(final Tag<T> tag, final T value) {
            return withTag(tag.getKey(), value);
        }

        @Override
        public DDSpanBuilder withStartTimestamp(final long timestampMicroseconds) {
            timestampMicro = timestampMicroseconds;
            return this;
        }

        public DDSpanBuilder withServiceName(final String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public DDSpanBuilder withResourceName(final String resourceName) {
            this.resourceName = resourceName;
            return this;
        }

        public DDSpanBuilder withErrorFlag() {
            errorFlag = true;
            return this;
        }

        public DDSpanBuilder withSpanType(final String spanType) {
            this.spanType = spanType;
            return this;
        }

        public Iterable<Map.Entry<String, String>> baggageItems() {
            if (parent == null) {
                return Collections.emptyList();
            }
            return parent.baggageItems();
        }

        public DDSpanBuilder withLogHandler(final LogHandler logHandler) {
            if (logHandler != null) {
                this.logHandler = logHandler;
            }
            return this;
        }

        public DDSpanBuilder withInternalLogger(final InternalLogger internalLogger) {
            if (internalLogger != null) {
                this.internalLogger = internalLogger;
            }
            return this;
        }

        @Override
        public DDSpanBuilder asChildOf(final Span span) {
            return asChildOf(span == null ? null : span.context());
        }

        @Override
        public DDSpanBuilder asChildOf(final SpanContext spanContext) {
            parent = spanContext;
            return this;
        }

        @Override
        public DDSpanBuilder addReference(final String referenceType, final SpanContext spanContext) {
            if (spanContext == null) {
                return this;
            }
            if (!(spanContext instanceof ExtractedContext) && !(spanContext instanceof DDSpanContext)) {
                return this;
            }
            if (References.CHILD_OF.equals(referenceType)
                    || References.FOLLOWS_FROM.equals(referenceType)) {
                return asChildOf(spanContext);
            } else {
            }
            return this;
        }

        public DDSpanBuilder withOrigin(final String origin) {
            this.origin = origin;
            return this;
        }

        // Private methods
        private DDSpanBuilder withTag(final String tag, final Object value) {
            if (value == null || (value instanceof String && ((String) value).isEmpty())) {
                tags.remove(tag);
            } else {
                tags.put(tag, value);
            }
            return this;
        }

        private BigInteger generateNewSpanId() {
            // It is **extremely** unlikely to generate the value "0" but we still need to handle that
            // case
            BigInteger value;
            do {
                synchronized (random) {
                    value = new StringCachingBigInteger(63, random);
                }
            } while (value.signum() == 0);

            return value;
        }

        private BigInteger generateNewTraceId() {
            BigInteger value;
            do {
                synchronized (idGenerationStrategy) {
                    value = new BigInteger(idGenerationStrategy.generateTraceId().toHexString(), 16);
                }
            } while (value.signum() == 0);

            return value;
        }

        /**
         * Build the SpanContext, if the actual span has a parent, the following attributes must be
         * propagated: - ServiceName - Baggage - Trace (a list of all spans related) - SpanType
         *
         * @return the context
         */
        private DDSpanContext buildSpanContext() {
            final BigInteger traceId;
            final BigInteger spanId = generateNewSpanId();
            final BigInteger parentSpanId;
            final Map<String, String> baggage;
            final PendingTrace parentTrace;
            final int samplingPriority;
            final String origin;

            final DDSpanContext context;
            SpanContext parentContext = parent;
            if (parentContext == null && !ignoreScope) {
                // use the Scope as parent unless overridden or ignored.
                final Span activeSpan = scopeManager.activeSpan();
                if (activeSpan != null) {
                    parentContext = activeSpan.context();
                }
            }

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
                if (serviceName == null) {
                    serviceName = ddsc.getServiceName();
                }

            } else {
                if (parentContext instanceof ExtractedContext) {
                    // Propagate external trace
                    final ExtractedContext extractedContext = (ExtractedContext) parentContext;
                    traceId = extractedContext.getTraceId();
                    parentSpanId = extractedContext.getSpanId();
                    samplingPriority = extractedContext.getSamplingPriority();
                    baggage = extractedContext.getBaggage();
                } else {
                    // Start a new trace
                    traceId = generateNewTraceId();
                    parentSpanId = BigInteger.ZERO;
                    samplingPriority = PrioritySampling.UNSET;
                    baggage = null;
                }

                // Get header tags and set origin whether propagating or not.
                if (parentContext instanceof TagContext) {
                    tags.putAll(((TagContext) parentContext).getTags());
                    origin = ((TagContext) parentContext).getOrigin();
                } else {
                    origin = this.origin;
                }

                tags.putAll(localRootSpanTags);

                parentTrace = new PendingTrace(DDTracer.this, traceId, internalLogger);
            }

            if (serviceName == null) {
                serviceName = DDTracer.this.serviceName;
            }

            final String operationName = this.operationName != null ? this.operationName : resourceName;

            // some attributes are inherited from the parent
            context =
                    new DDSpanContext(
                            traceId,
                            spanId,
                            parentSpanId,
                            serviceName,
                            operationName,
                            resourceName,
                            samplingPriority,
                            origin,
                            baggage,
                            errorFlag,
                            spanType,
                            tags,
                            parentTrace,
                            DDTracer.this,
                            serviceNameMappings,
                            internalLogger);

            // Apply Decorators to handle any tags that may have been set via the builder.
            for (final Map.Entry<String, Object> tag : tags.entrySet()) {
                if (tag.getValue() == null) {
                    context.setTag(tag.getKey(), null);
                    continue;
                }

                boolean addTag = true;

                // Call decorators
                final List<AbstractDecorator> decorators = getSpanContextDecorators(tag.getKey());
                if (decorators != null) {
                    for (final AbstractDecorator decorator : decorators) {
                        try {
                            addTag &= decorator.shouldSetTag(context, tag.getKey(), tag.getValue());
                        } catch (final Throwable ex) {
                        }
                    }
                }

                if (!addTag) {
                    context.setTag(tag.getKey(), null);
                }
            }

            return context;
        }
    }

    private static class ShutdownHook extends Thread {
        private final WeakReference<DDTracer> reference;

        private ShutdownHook(final DDTracer tracer) {
            super("dd-tracer-shutdown-hook");
            reference = new WeakReference<>(tracer);
        }

        @Override
        public void run() {
            final DDTracer tracer = reference.get();
            if (tracer != null) {
                tracer.close();
            }
        }
    }

    // GENERATED GETTER

    public int getPartialFlushMinSpans() {
        return partialFlushMinSpans;
    }
}
