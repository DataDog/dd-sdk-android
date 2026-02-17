/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace

import androidx.annotation.FloatRange
import com.datadog.android.api.SdkCore
import com.datadog.android.core.configuration.HostsSanitizer
import com.datadog.android.core.internal.net.DefaultFirstPartyHostHeaderTypeResolver
import com.datadog.android.core.sampling.Sampler
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.trace.internal.ApmNetworkInstrumentation
import com.datadog.android.trace.internal.net.TracerProvider

/**
 * Configuration that allows to configure APM tracing for network requests.
 *
 * @param tracedHostsWithHeaderType a list of all the hosts and header types that you want to
 * be automatically tracked by this interceptor. If registering a [GlobalDatadogTracer], the tracer must be
 * configured with [com.datadog.android.trace.api.tracer.DatadogTracerBuilder.withTracingHeadersTypes] containing all the necessary
 * header types configured for network tracking.
 */
@Suppress("TooManyFunctions")
class ApmNetworkInstrumentationConfiguration(
    internal val tracedHostsWithHeaderType: Map<String, Set<TracingHeaderType>>
) {
    internal var traceOrigin: String? = null
    internal var redacted404ResourceName = true
    internal var sdkInstanceName: String? = null

    internal var localTracerFactory = DEFAULT_LOCAL_TRACER_FACTORY
    internal var traceContextInjection = TraceContextInjection.SAMPLED
    internal var tracedRequestListener: NetworkTracedRequestListener = NoOpNetworkTracedRequestListener()
    internal var traceSampler: Sampler<DatadogSpan> =
        DeterministicTraceSampler(DEFAULT_TRACE_SAMPLE_RATE)
    internal var globalTracerProvider: () -> DatadogTracer? = { GlobalDatadogTracer.getOrNull() }
    internal var networkTracingScope: ApmNetworkTracingScope = ApmNetworkTracingScope.ALL

    constructor(
        tracedHosts: List<String>
    ) : this(
        tracedHosts.associateWith {
            setOf(
                TracingHeaderType.DATADOG,
                TracingHeaderType.TRACECONTEXT
            )
        }
    )

    /**
     * Set the origin of the trace.
     * @param traceOrigin the origin of the trace.
     */
    fun setTraceOrigin(traceOrigin: String) = apply {
        this.traceOrigin = traceOrigin
    }

    /**
     * Set the SDK instance name to bind to, the default value is null.
     * @param sdkInstanceName SDK instance name to bind to, the default value is null.
     * Instrumentation won't be working until SDK instance is ready.
     */
    fun setSdkInstanceName(sdkInstanceName: String) = apply {
        this.sdkInstanceName = sdkInstanceName
    }

    /**
     * Set the listener for automatically created [DatadogSpan]s.
     * @param tracedRequestListener a listener for automatically created [DatadogSpan]s
     */
    fun setTracedRequestListener(tracedRequestListener: NetworkTracedRequestListener) = apply {
        this.tracedRequestListener = tracedRequestListener
    }

    /**
     * Set the trace sample rate controlling the sampling of APM traces created for
     * auto-instrumented requests. If there is a parent trace attached to the network span created, then its
     * sampling decision will be used instead.
     * @param sampleRate the sample rate to use (percentage between 0f and 100f, default is 100f).
     */
    fun setTraceSampleRate(@FloatRange(from = 0.0, to = 100.0) sampleRate: Float) = apply {
        this.traceSampler = DeterministicTraceSampler(sampleRate)
    }

    /**
     * Set the trace sampler controlling the sampling of APM traces created for
     * auto-instrumented requests. If there is a parent trace attached to the network span created, then its
     * sampling decision will be used instead.
     * @param traceSampler the trace sampler controlling the sampling of APM traces.
     * By default it is a sampler accepting 100% of the traces.
     */
    fun setTraceSampler(traceSampler: Sampler<DatadogSpan>) = apply {
        this.traceSampler = traceSampler
    }

    /**
     * Set the trace context injection behavior for this interceptor in the intercepted requests.
     * By default this is set to [TraceContextInjection.SAMPLED], meaning that only the sampled request will
     * propagate the trace context. In case of [TraceContextInjection.ALL] all the trace context
     * will be propagated in the intercepted requests no matter if the span created around the request
     * is sampled or not.
     * @param traceContextInjection the trace context injection option.
     * @see TraceContextInjection.ALL
     * @see TraceContextInjection.SAMPLED
     */
    fun setTraceContextInjection(traceContextInjection: TraceContextInjection) = apply {
        this.traceContextInjection = traceContextInjection
    }

    /**
     * Set whether network requests returning a 404 status code should have their resource name redacted.
     * In order to reduce the cardinality of resource names in APM, 404 URLs are automatically redacted to
     * "404".
     * @param redacted if true, all 404 requests will have a resource name set to "404", else the resource name
     * will be the URL
     */
    fun set404ResourcesRedacted(redacted: Boolean) = apply {
        redacted404ResourceName = redacted
    }

    /**
     * Sets the tracing scope for network instrumentation.
     *
     * This controls how detailed the tracing will be:
     * - [ApmNetworkTracingScope.ALL]: Traces both application-level requests and internal
     *   network operations (redirects, retries). This is the default.
     * - [ApmNetworkTracingScope.APPLICATION_LEVEL_REQUESTS_ONLY]: Only traces the top-level
     *   application request, while still maintaining RUM-APM linking capabilities.
     *
     * @param networkTracingScope the tracing scope to use
     * @see ApmNetworkTracingScope
     */
    fun setTraceScope(networkTracingScope: ApmNetworkTracingScope) = apply {
        this.networkTracingScope = networkTracingScope
    }

    internal fun setLocalTracerFactory(factory: (SdkCore, Set<TracingHeaderType>) -> DatadogTracer) = apply {
        this.localTracerFactory = factory
    }

    internal fun setGlobalTracerProvider(globalTracerProvider: () -> DatadogTracer?) = apply {
        this.globalTracerProvider = globalTracerProvider
    }

    internal companion object {
        internal const val ALL_IN_SAMPLE_RATE: Double = 100.0
        internal const val DEFAULT_TRACE_SAMPLE_RATE: Float = 100f
        internal const val NETWORK_REQUESTS_TRACKING_FEATURE_NAME = "Network Requests"

        internal fun ApmNetworkInstrumentationConfiguration.createInstrumentation(
            instrumentationName: String
        ): ApmNetworkInstrumentation {
            val localFirstPartyHostHeaderTypeResolver = DefaultFirstPartyHostHeaderTypeResolver(
                resolveHosts(tracedHostsWithHeaderType)
            )

            val tracerProvider = TracerProvider(localTracerFactory, globalTracerProvider)

            return ApmNetworkInstrumentation(
                traceOrigin = traceOrigin,
                traceSampler = traceSampler,
                tracerProvider = tracerProvider,
                sdkInstanceName = sdkInstanceName,
                injectionType = traceContextInjection,
                networkTracingScope = networkTracingScope,
                networkingLibraryName = instrumentationName,
                tracedRequestListener = tracedRequestListener,
                redacted404ResourceName = redacted404ResourceName,
                localFirstPartyHostHeaderTypeResolver = localFirstPartyHostHeaderTypeResolver
            )
        }

        private fun resolveHosts(
            tracedHosts: Map<String, Set<TracingHeaderType>>
        ): Map<String, Set<TracingHeaderType>> {
            val sanitizer = HostsSanitizer()
            val sanitizedHosts = sanitizer.sanitizeHosts(
                tracedHosts.keys.toList(),
                NETWORK_REQUESTS_TRACKING_FEATURE_NAME
            )

            return tracedHosts.filterKeys { sanitizedHosts.contains(it) }
        }

        private val DEFAULT_LOCAL_TRACER_FACTORY: (SdkCore, Set<TracingHeaderType>) -> DatadogTracer =
            { sdkCore, tracingHeaderTypes: Set<TracingHeaderType> ->
                DatadogTracing.newTracerBuilder(sdkCore)
                    .withTracingHeadersTypes(tracingHeaderTypes)
                    .withSampleRate(ALL_IN_SAMPLE_RATE)
                    .build()
            }
    }
}
