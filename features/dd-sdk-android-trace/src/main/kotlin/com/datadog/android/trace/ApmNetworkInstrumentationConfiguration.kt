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
 * Configuration for APM distributed tracing of network requests.
 *
 * This class controls how the Datadog SDK instruments outgoing HTTP requests
 * with distributed tracing headers and optional client-side APM spans.
 *
 * At minimum, you must provide a list of first-party hosts (or a map of hosts
 * to [TracingHeaderType]s) so that the SDK knows which requests to instrument.
 *
 * Example usage:
 * ```kotlin
 * val apmConfig = ApmNetworkInstrumentationConfiguration(
 *     listOf("api.example.com", "cdn.example.com")
 * )
 *     .setTraceSampleRate(75f)
 *     .setTraceContextInjection(TraceContextInjection.ALL)
 * ```
 *
 * @see TracingHeaderType
 * @see TraceContextInjection
 * @see ApmNetworkTracingScope
 */
@Suppress("TooManyFunctions")
class ApmNetworkInstrumentationConfiguration internal constructor(
    internal val tracedHostsWithHeaderType: Map<String, Set<TracingHeaderType>>,
    internal var traceOrigin: String? = null,
    internal var redacted404ResourceName: Boolean = true,
    internal var sdkInstanceName: String? = null,
    internal var localTracerFactory: (SdkCore, Set<TracingHeaderType>) -> DatadogTracer = DEFAULT_LOCAL_TRACER_FACTORY,
    internal var traceContextInjection: TraceContextInjection = TraceContextInjection.SAMPLED,
    internal var tracedRequestListener: NetworkTracedRequestListener = NoOpNetworkTracedRequestListener(),
    internal var traceSampler: Sampler<DatadogSpan> = DeterministicTraceSampler(DEFAULT_TRACE_SAMPLE_RATE),
    internal var globalTracerProvider: () -> DatadogTracer? = { GlobalDatadogTracer.getOrNull() },
    internal var networkTracingScope: ApmNetworkTracingScope = ApmNetworkTracingScope.EXCLUDE_INTERNAL_REDIRECTS,
    internal var headerPropagationOnly: Boolean = false
) {
    /**
     * Creates a configuration with a list of traced hosts using default header types
     * ([TracingHeaderType.DATADOG] and [TracingHeaderType.TRACECONTEXT]).
     *
     * @param tracedHosts a list of hosts to trace.
     */
    constructor(tracedHosts: List<String>) : this(
        tracedHosts.associateWith {
            setOf(
                TracingHeaderType.DATADOG,
                TracingHeaderType.TRACECONTEXT
            )
        }
    )

    /**
     * Creates a configuration with a map of hosts to their associated tracing header types.
     *
     * @param tracedHostsWithHeaderType a map of host names to sets of [TracingHeaderType]
     * to use for each host.
     */
    constructor(tracedHostsWithHeaderType: Map<String, Set<TracingHeaderType>>) : this(
        tracedHostsWithHeaderType,
        headerPropagationOnly = false // this line added in order to use main constructor and prevent loop
    )

    /**
     * Set the origin of the trace.
     * @param traceOrigin the origin of the trace.
     */
    fun setTraceOrigin(traceOrigin: String, replace: Boolean = true) = apply {
        if (replace || this.traceOrigin == null) {
            this.traceOrigin = traceOrigin
        }
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
     * Set the trace context injection behavior for the intercepted requests.
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
     * - [ApmNetworkTracingScope.EXCLUDE_INTERNAL_REDIRECTS] (default): Only traces the top-level
     *   application request, while still maintaining RUM-APM linking capabilities.
     * - [ApmNetworkTracingScope.ALL]: Traces both application-level requests and internal
     *   network operations (redirects, retries).
     *
     * @param networkTracingScope the tracing scope to use
     * @see ApmNetworkTracingScope
     */
    fun setTraceScope(networkTracingScope: ApmNetworkTracingScope) = apply {
        this.networkTracingScope = networkTracingScope
    }

    /**
     * Disables client-side APM span reporting while keeping tracing header propagation active.
     *
     * When called, the SDK will still inject distributed tracing headers
     * (e.g. `x-datadog-trace-id`, `x-datadog-parent-id`) into outgoing requests for
     * first-party hosts, enabling RUM-APM linking and end-to-end distributed traces.
     * However, no client-side APM spans will be sent to the Datadog backend.
     *
     * This is useful when you want distributed tracing visibility without the overhead
     * of client-side network spans.
     */
    fun setHeaderPropagationOnly() = apply {
        headerPropagationOnly = true
    }

    /**
     * Returns whether this configuration is set to header-propagation-only mode.
     *
     * @return `true` if client-side APM spans are disabled and only tracing headers
     * are propagated, `false` otherwise.
     * @see headerPropagationOnly
     */
    fun isHeaderPropagationOnly(): Boolean {
        return headerPropagationOnly
    }

    /**
     * Creates a deep copy of this configuration.
     *
     * Mutable collection fields are deeply copied to ensure the returned instance is independent.
     * Immutable and shared-by-design fields (scalars, enums, lambdas) are reused as-is.
     *
     * @return a new [ApmNetworkInstrumentationConfiguration] with the same settings.
     */
    fun copy() = ApmNetworkInstrumentationConfiguration(
        tracedHostsWithHeaderType = tracedHostsWithHeaderType.deepCopy(),
        traceOrigin = traceOrigin,
        redacted404ResourceName = redacted404ResourceName,
        sdkInstanceName = sdkInstanceName,
        localTracerFactory = localTracerFactory,
        traceContextInjection = traceContextInjection,
        tracedRequestListener = tracedRequestListener,
        traceSampler = traceSampler,
        globalTracerProvider = globalTracerProvider,
        networkTracingScope = networkTracingScope,
        headerPropagationOnly = headerPropagationOnly
    )

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
                canSendSpan = !headerPropagationOnly,
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

        private fun Map<String, Set<TracingHeaderType>>.deepCopy() = mapValues { (_, v) -> v.toSet() }

        private val DEFAULT_LOCAL_TRACER_FACTORY: (SdkCore, Set<TracingHeaderType>) -> DatadogTracer =
            { sdkCore, tracingHeaderTypes: Set<TracingHeaderType> ->
                DatadogTracing.newTracerBuilder(sdkCore)
                    .withTracingHeadersTypes(tracingHeaderTypes)
                    .withSampleRate(ALL_IN_SAMPLE_RATE)
                    .build()
            }
    }
}
