/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.HttpRequestInfoModifier
import com.datadog.android.api.instrumentation.network.HttpResponseInfo
import com.datadog.android.api.logToUser
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.SdkReference
import com.datadog.android.core.internal.net.DefaultFirstPartyHostHeaderTypeResolver
import com.datadog.android.core.sampling.Sampler
import com.datadog.android.internal.utils.loggableStackTrace
import com.datadog.android.internal.utils.runIfNull
import com.datadog.android.trace.api.DatadogTracingConstants.Tags
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.trace.internal.DatadogTracingToolkit.propagationHelper
import com.datadog.android.trace.internal.RumContextPropagator
import com.datadog.android.trace.internal.RumContextPropagator.Companion.extractRumContext
import com.datadog.android.trace.internal.net.RequestTraceState
import com.datadog.android.trace.internal.net.TracerProvider
import com.datadog.android.trace.internal.net.applyPriority
import com.datadog.android.trace.internal.net.buildSpan
import com.datadog.android.trace.internal.net.finishRumAware
import com.datadog.android.trace.internal.net.isRumEnabled
import com.datadog.android.trace.internal.net.sample
import java.net.HttpURLConnection
import java.util.Locale

/**
 * For internal usage only.
 *
 * Provides APM (Application Performance Monitoring) tracing instrumentation for network requests.
 * This class handles the creation and management of trace spans for HTTP requests, including
 * header injection, sampling decisions, and span lifecycle management.
 *
 * Use [NetworkTracingInstrumentation.Configuration] to create instances of this class.
 *
 * @param sdkInstanceName the name of the SDK instance to bind to, or null for the default instance.
 * @param traceOrigin optional origin tag to add to traces.
 * @param tracerProvider provider for obtaining tracer instances.
 * @param redacted404ResourceName whether to redact resource names for 404 responses.
 * @param traceSampler the sampler used to determine which traces should be sampled.
 * @param injectionType defines whether trace context should be injected into all requests or only sampled ones.
 * @param tracedRequestListener listener to be notified when a request is intercepted.
 * @param localFirstPartyHostHeaderTypeResolver resolver for determining header types for first-party hosts.
 * @param networkInstrumentationName the name identifying the network instrumentation (e.g., "OkHttp", "Cronet").
 * @param rumApmLinkingEnabled whether RUM-APM linking is enabled.
 * @param networkInstrumentationEnabled whether network instrumentation is enabled.
 */
@Suppress("LongParameterList")
class NetworkTracingInstrumentation internal constructor(
    internal val sdkInstanceName: String?,
    internal val traceOrigin: String?,
    internal val tracerProvider: TracerProvider,
    internal val redacted404ResourceName: Boolean,
    internal val traceSampler: Sampler<DatadogSpan>,
    internal val injectionType: TraceContextInjection,
    internal val tracedRequestListener: NetworkTracedRequestListener,
    internal val localFirstPartyHostHeaderTypeResolver: DefaultFirstPartyHostHeaderTypeResolver,
    private val networkInstrumentationName: String,
    private val rumApmLinkingEnabled: Boolean = true,
    private val networkInstrumentationEnabled: Boolean = true
) {
    private val rumContextPropagator = RumContextPropagator { internalSdkCore }
    private val sdkCoreReference = SdkReference(sdkInstanceName) {
        val sdkCore = it as InternalSdkCore
        if (localFirstPartyHostHeaderTypeResolver.isEmpty() && sdkCore.firstPartyHostResolver.isEmpty()) {
            sdkCore.internalLogger.logToUser(InternalLogger.Level.WARN, onlyOnce = true) {
                WARNING_TRACING_NO_HOSTS.format(Locale.US, networkInstrumentationName)
            }
        }
    }
    private val internalSdkCore: InternalSdkCore?
        get() = sdkCoreReference.get() as? InternalSdkCore

    /**
     * Called when a network request is about to be sent.
     * This method creates a trace span, applies sampling decisions, and injects tracing headers.
     *
     * @param request the HTTP request information.
     * @return the tracing state containing the request modifier, sampling decision, and span.
     */
    @Suppress("ReturnCount")
    fun onRequest(request: HttpRequestInfo): RequestTraceState {
        val requestModifier = request.modify()
        val sdkCore = getSdkCoreOrNull(request.url) ?: return RequestTraceState(requestModifier)
        val tracer = tracerProvider.provideTracer(
            sdkCore,
            localFirstPartyHostHeaderTypeResolver.getAllHeaderTypes(),
            networkInstrumentationName
        ) ?: return RequestTraceState(requestModifier)
        if (!networkInstrumentationEnabled || !request.isTraceable(sdkCore)) {
            return RequestTraceState(requestModifier)
        }

        val span = tracer.buildSpan(request, networkInstrumentationName, traceOrigin)
        val isSampled = span
            .extractRumContext(rumContextPropagator, block = true)
            .sample(request, traceSampler)

        if (span.isRootSpan) {
            span.applyPriority(isSampled, traceSampler)
        }

        val updatedRequest = try {
            updateRequest(request.url, sdkCore, requestModifier, tracer, span, isSampled)
        } catch (e: IllegalStateException) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { "Failed to update intercepted $networkInstrumentationName request" },
                e
            )
            requestModifier
        }

        return RequestTraceState(
            span = span,
            isSampled = isSampled,
            requestModifier = updatedRequest,
            sampleRate = traceSampler.getSampleRate(),
            rumApmLinkingEnabled = rumApmLinkingEnabled
        )
    }

    /**
     * Called when a network request succeeds.
     * This method updates the span with response information and finishes it.
     *
     * @param tracingState the tracing state from [onRequest].
     * @param response the HTTP response information.
     */
    fun onResponseSucceed(tracingState: RequestTraceState, response: HttpResponseInfo) {
        if (tracingState.isSampled) {
            tracingState.span?.setTag(Tags.KEY_HTTP_STATUS, response.statusCode)
            if (response.statusCode in HttpURLConnection.HTTP_BAD_REQUEST until HttpURLConnection.HTTP_INTERNAL_ERROR) {
                tracingState.span?.isError = true
            }
            if (response.statusCode == HttpURLConnection.HTTP_NOT_FOUND && redacted404ResourceName) {
                tracingState.span?.resourceName = RESOURCE_NAME_404
            }
        }
        tracingState.onRequestCompleted(response, null)
        tracingState.span?.finishRumAware(tracingState.isSampled, !internalSdkCore.isRumEnabled)
    }

    /**
     * Called when a network request fails.
     * This method marks the span as errored, adds error details, and finishes it.
     *
     * @param tracingState the tracing state from [onRequest].
     * @param throwable the exception that caused the failure.
     */
    fun onResponseFailed(tracingState: RequestTraceState, throwable: Throwable) {
        if (!tracingState.isSampled) {
            tracingState.onRequestCompleted(null, throwable)
        } else {
            tracingState.span?.isError = true
            tracingState.span?.setTag(Tags.KEY_ERROR_MSG, throwable.message)
            tracingState.span?.setTag(Tags.KEY_ERROR_TYPE, throwable.javaClass.name)
            tracingState.span?.setTag(Tags.KEY_ERROR_STACK, throwable.loggableStackTrace())
            tracingState.onRequestCompleted(null, throwable)
        }
        tracingState.span?.finishRumAware(tracingState.isSampled, !internalSdkCore.isRumEnabled)
    }

    private fun RequestTraceState.onRequestCompleted(
        response: HttpResponseInfo?,
        throwable: Throwable?
    ) {
        if (!isSampled || span == null) return
        val request = requestModifier.result()
        try {
            tracedRequestListener.onRequestIntercepted(request, span, response, throwable)
        } catch (e: StackOverflowError) {
            getSdkCoreOrNull(request.url)?.internalLogger?.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { "$ERROR_STACK_OVERFLOW\nRequest: ${request.method}:${request.url}" },
                e
            )
            @Suppress("ThrowingInternalException")
            throw e
        }
    }

    private fun updateRequest(
        url: String,
        sdkCore: InternalSdkCore,
        requestModifier: HttpRequestInfoModifier,
        tracer: DatadogTracer,
        span: DatadogSpan,
        isSampled: Boolean
    ): HttpRequestInfoModifier = requestModifier.also { modifier ->
        val tracingHeaderTypes = localFirstPartyHostHeaderTypeResolver.headerTypesForUrl(url)
            .ifEmpty {
                sdkCore.firstPartyHostResolver.headerTypesForUrl(url)
            }

        if (isSampled) {
            propagationHelper.propagateSampledHeaders(requestModifier, tracer, span, tracingHeaderTypes)
        } else {
            propagationHelper.propagateNotSampledHeaders(
                modifier,
                tracer,
                span,
                tracingHeaderTypes,
                injectionType,
                traceOrigin
            )
        }
    }

    private fun HttpRequestInfo.isTraceable(sdkCore: InternalSdkCore): Boolean =
        sdkCore.firstPartyHostResolver.isFirstPartyUrl(url) ||
            localFirstPartyHostHeaderTypeResolver.isFirstPartyUrl(url)

    private fun getSdkCoreOrNull(url: String? = null) = internalSdkCore.runIfNull {
        InternalLogger.UNBOUND.logToUser(InternalLogger.Level.INFO) {
            buildString {
                append(
                    if (sdkInstanceName == null) "Default SDK instance" else "SDK instance with name=$sdkInstanceName"
                )
                append(" for ").append(networkInstrumentationName).append(" instrumentation is not found")
                if (url != null) append(", skipping tracking of request with url=").append(url)
            }
        }
    }

    companion object {
        internal const val ZERO_SAMPLE_RATE: Float = 0.0f
        internal const val ALL_IN_SAMPLE_RATE: Double = 100.0
        internal const val SPAN_NAME = "%s.request"
        internal const val RESOURCE_NAME_404 = "404"
        internal const val AGENT_PSR_ATTRIBUTE = "_dd.agent_psr"
        internal const val URL_QUERY_PARAMS_BLOCK_SEPARATOR = '?'
        internal const val WARNING_TRACING_NO_HOSTS =
            "You added a TraceInstrumentation to your %s instrumentation, " +
                "but you did not specify any first party hosts. " +
                "Your requests won't be traced.\n" +
                "To set a list of known hosts, you can use the " +
                "Configuration.Builder::setFirstPartyHosts() method."
        internal const val ERROR_STACK_OVERFLOW =
            "StackOverflowError detected in TracedRequestListener. " +
                "This is likely caused by retrying the same request within the " +
                "onRequestIntercepted callback, leading to infinite recursion."

        /**
         * Creates a new [NetworkTracingInstrumentationConfiguration] with the specified traced hosts and header types.
         *
         * @param tracedHostsWithHeaderType a map of host names to the set of tracing header types to use.
         * @return a new builder instance.
         */
        @JvmStatic
        @Suppress("FunctionName")
        fun Configuration(
            tracedHostsWithHeaderType: Map<String, Set<TracingHeaderType>>
        ) = NetworkTracingInstrumentationConfiguration(tracedHostsWithHeaderType)

        /**
         * Creates a new [NetworkTracingInstrumentationConfiguration] with the specified traced hosts.
         * Uses [TracingHeaderType.DATADOG] and [TracingHeaderType.TRACECONTEXT] header types by default.
         *
         * @param tracedHosts a list of host names to trace.
         * @return a new builder instance.
         */
        @JvmStatic
        @Suppress("FunctionName")
        fun Configuration(
            tracedHosts: List<String>
        ) = NetworkTracingInstrumentationConfiguration(
            tracedHosts.associateWith {
                setOf(
                    TracingHeaderType.DATADOG,
                    TracingHeaderType.TRACECONTEXT
                )
            }
        )
    }
}
