/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.FeatureContextUpdateReceiver
import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.HttpRequestInfoBuilder
import com.datadog.android.api.instrumentation.network.HttpResponseInfo
import com.datadog.android.api.instrumentation.network.MutableHttpRequestInfo
import com.datadog.android.api.logToUser
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.SdkReference
import com.datadog.android.core.internal.net.DefaultFirstPartyHostHeaderTypeResolver
import com.datadog.android.core.sampling.Sampler
import com.datadog.android.internal.utils.loggableStackTrace
import com.datadog.android.lint.InternalApi
import com.datadog.android.trace.ApmNetworkTracingScope
import com.datadog.android.trace.NetworkTracedRequestListener
import com.datadog.android.trace.TraceContextInjection
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.api.DatadogTracingConstants
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.trace.internal.RumContextPropagator.Companion.extractRumContext
import com.datadog.android.trace.internal._TraceInternalProxy.propagationHelper
import com.datadog.android.trace.internal.net.RequestTracingState
import com.datadog.android.trace.internal.net.TracerProvider
import com.datadog.android.trace.internal.net.applyPriority
import com.datadog.android.trace.internal.net.buildSpan
import com.datadog.android.trace.internal.net.effectiveSampleRate
import com.datadog.android.trace.internal.net.finishRumAware
import com.datadog.android.trace.internal.net.sample
import java.net.HttpURLConnection
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * For internal usage only.
 *
 * Provides APM (Application Performance Monitoring) tracing instrumentation for network requests.
 * This class handles the creation and management of trace spans for HTTP requests, including
 * header injection, sampling decisions, and span lifecycle management.
 *
 * @param canSendSpan whether APM span sending is enabled. When false, spans are created for RUM-APM linking
 *        but not sent to the backend.
 * @param sdkInstanceName the name of the SDK instance to bind to, or null for the default instance.
 * @param traceOrigin optional origin tag to add to traces.
 * @param tracerProvider provider for obtaining tracer instances.
 * @param redacted404ResourceName whether to redact resource names for 404 responses.
 * @param traceSampler the sampler used to determine which traces should be sampled.
 * @param injectionType defines whether trace context should be injected into all requests or only sampled ones.
 * @param tracedRequestListener listener to be notified when a request is intercepted.
 * @param localFirstPartyHostHeaderTypeResolver resolver for determining header types for first-party hosts.
 * @param networkingLibraryName the name identifying the network instrumentation (e.g., "OkHttp", "Cronet").
 * @param networkTracingScope Tracing scope for the instrumentation. See [ApmNetworkTracingScope] enum for more details.
 * @param sessionSampleRateReceiver optional receiver that caches the RUM session sample rate for sampling decisions. When provided, it is registered with the SDK on first resolution and updated whenever the RUM context changes.
 */
@Suppress("LongParameterList")
@InternalApi
class ApmNetworkInstrumentation internal constructor(
    internal val canSendSpan: Boolean,
    val sdkInstanceName: String?,
    val traceOrigin: String?,
    internal val tracerProvider: TracerProvider,
    internal val redacted404ResourceName: Boolean,
    internal val traceSampler: Sampler<DatadogSpan>,
    internal val injectionType: TraceContextInjection,
    internal val tracedRequestListener: NetworkTracedRequestListener,
    internal val localFirstPartyHostHeaderTypeResolver: DefaultFirstPartyHostHeaderTypeResolver,
    private val networkingLibraryName: String,
    val networkTracingScope: ApmNetworkTracingScope = ApmNetworkTracingScope.ALL,
    internal val sessionSampleRateReceiver: FeatureContextUpdateReceiver? = null
) {
    private val rumContextPropagator = RumContextPropagator { internalSdkCore }
    private val receiverLifecycleLock = Any()
    private val isClosed = AtomicBoolean(false)
    private val isRegistered = AtomicBoolean(false)

    private val internalSdkCore: InternalSdkCore?
        get() = sdkCoreReference.get() as? InternalSdkCore

    /** The SDK core instance, if available. */
    val sdkCore: SdkCore?
        get() = sdkCoreReference.get()

    /** Reference to the SDK core instance. */
    val sdkCoreReference: SdkReference = SdkReference(sdkInstanceName) {
        val sdkCore = it as InternalSdkCore
        if (localFirstPartyHostHeaderTypeResolver.isEmpty() && sdkCore.firstPartyHostResolver.isEmpty()) {
            sdkCore.internalLogger.logToUser(InternalLogger.Level.WARN, onlyOnce = true) {
                WARNING_TRACING_NO_HOSTS.format(Locale.US, networkingLibraryName)
            }
        }
        sessionSampleRateReceiver?.let { receiver ->
            synchronized(receiverLifecycleLock) {
                if (!isClosed.get() && !isRegistered.get()) {
                    sdkCore.setContextUpdateReceiver(receiver)
                    isRegistered.set(true)
                }
            }
        }
    }

    /** The sample rate used by the trace sampler, or null if unavailable. */
    val sampleRate: Float?
        get() = traceSampler.getSampleRate()

    /** The set of tracing header types configured for locally defined first-party hosts. */
    val localHeaderTypes: Set<TracingHeaderType>
        get() = localFirstPartyHostHeaderTypeResolver.getAllHeaderTypes()

    /**
     * Called when a network request is about to be sent.
     * This method creates a trace span, applies sampling decisions, and injects tracing headers.
     *
     * @param request the HTTP request information.
     * @return the tracing state containing the request modifier, sampling decision, and span.
     */
    @Suppress("ReturnCount")
    fun onRequest(request: HttpRequestInfo): RequestTracingState? {
        val sdkCore = getSdkCoreOrNull(request.url)
        val requestInfoBuilder = request.newBuilder(sdkCore?.internalLogger ?: InternalLogger.UNBOUND)
        if (requestInfoBuilder == null) {
            return null
        }

        if (sdkCore == null) {
            return RequestTracingState(requestInfoBuilder)
        }

        val tracer = tracerProvider.provideTracer(
            sdkCore,
            localHeaderTypes,
            networkingLibraryName
        )

        if (tracer == null || !request.isTraceable(sdkCore)) {
            return RequestTracingState(requestInfoBuilder)
        }

        val span = tracer.buildSpan(request, networkingLibraryName, traceOrigin)
        val isSampled = span.extractRumContext(rumContextPropagator, block = true).sample(request, traceSampler)
        if (span.isRootSpan) {
            span.applyPriority(isSampled, traceSampler)
        }

        val tracedRequestInfoBuilder = try {
            traceRequest(request.url, sdkCore, requestInfoBuilder, tracer, span, isSampled)
        } catch (e: IllegalStateException) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { "Failed to update intercepted $networkingLibraryName request" },
                e
            )
            requestInfoBuilder
        }

        return RequestTracingState(
            span = span,
            isSampled = isSampled,
            sampleRate = traceSampler.effectiveSampleRate(span),
            requestInfoBuilder = tracedRequestInfoBuilder
        )
    }

    /**
     * Called when a network request succeeds.
     * This method updates the span with response information and finishes it.
     *
     * @param requestTracingState the tracing state from [onRequest].
     * @param response the HTTP response information.
     */
    fun onResponseSucceeded(requestTracingState: RequestTracingState, response: HttpResponseInfo) {
        if (requestTracingState.isSampled) {
            requestTracingState.span?.setTag(DatadogTracingConstants.Tags.KEY_HTTP_STATUS, response.statusCode)
            if (response.statusCode in HttpURLConnection.HTTP_BAD_REQUEST until HttpURLConnection.HTTP_INTERNAL_ERROR) {
                requestTracingState.span?.isError = true
            }
            if (response.statusCode == HttpURLConnection.HTTP_NOT_FOUND && redacted404ResourceName) {
                requestTracingState.span?.resourceName = RESOURCE_NAME_404
            }
        }
        requestTracingState.onRequestIntercepted(response, null)
        requestTracingState.span?.finishRumAware(requestTracingState.isSampled, canSendSpan)
    }

    /**
     * Called when a network request fails.
     * This method marks the span as errored, adds error details, and finishes it.
     *
     * @param requestTracingState the tracing state from [onRequest].
     * @param throwable the exception that caused the failure.
     */
    fun onResponseFailed(requestTracingState: RequestTracingState, throwable: Throwable) {
        if (requestTracingState.isSampled) {
            requestTracingState.span?.isError = true
            requestTracingState.span?.setTag(DatadogTracingConstants.Tags.KEY_ERROR_MSG, throwable.message)
            requestTracingState.span?.setTag(DatadogTracingConstants.Tags.KEY_ERROR_TYPE, throwable.javaClass.name)
            requestTracingState.span?.setTag(
                DatadogTracingConstants.Tags.KEY_ERROR_STACK,
                throwable.loggableStackTrace()
            )
        }
        requestTracingState.onRequestIntercepted(null, throwable)
        requestTracingState.span?.finishRumAware(requestTracingState.isSampled, canSendSpan)
    }

    /**
     * Removes all tracing headers from the request.
     * This is useful when starting a new independent trace for redirect requests,
     * ensuring each redirect creates a separate root span.
     *
     * @param requestBuilder the request builder to remove headers from.
     */
    fun removeTracingHeaders(requestBuilder: HttpRequestInfoBuilder) {
        propagationHelper.removeAllTracingHeaders(requestBuilder)
    }

    /**
     * Reports an instrumentation error to the internal logger.
     * @param messageBuilder the error message to report
     */
    fun reportInstrumentationError(messageBuilder: () -> String) {
        internalSdkCore?.internalLogger?.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            messageBuilder
        )
    }

    /**
     * Releases this instrumentation and unregisters context receivers from SDK core.
     */
    fun close() {
        val receiver = sessionSampleRateReceiver ?: return
        synchronized(receiverLifecycleLock) {
            isClosed.set(true)
            if (isRegistered.compareAndSet(true, false)) {
                internalSdkCore?.removeContextUpdateReceiver(receiver)
            }
        }
    }

    private fun RequestTracingState.onRequestIntercepted(
        response: HttpResponseInfo?,
        throwable: Throwable?
    ) {
        if (span == null) return
        val request = createRequestInfo()
        try {
            tracedRequestListener.onRequestIntercepted(request, span, response, throwable)
        } catch (e: StackOverflowError) {
            internalSdkCore?.internalLogger?.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { "$ERROR_STACK_OVERFLOW\nRequest: ${request.method}:${request.url}" },
                e
            )
            @Suppress("ThrowingInternalException")
            throw e
        }
    }

    private fun traceRequest(
        url: String,
        sdkCore: InternalSdkCore,
        requestBuilder: HttpRequestInfoBuilder,
        tracer: DatadogTracer,
        span: DatadogSpan,
        isSampled: Boolean
    ): HttpRequestInfoBuilder = requestBuilder.also { builder ->
        val tracingHeaderTypes = localFirstPartyHostHeaderTypeResolver.headerTypesForUrl(url)
            .ifEmpty { sdkCore.firstPartyHostResolver.headerTypesForUrl(url) }

        if (isSampled) {
            propagationHelper.propagateSampledHeaders(
                builder,
                tracer,
                span,
                tracingHeaderTypes
            )
        } else {
            propagationHelper.propagateNotSampledHeaders(
                builder,
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

    private fun getSdkCoreOrNull(url: String? = null): InternalSdkCore? {
        if (internalSdkCore == null) {
            InternalLogger.UNBOUND.logToUser(InternalLogger.Level.INFO) {
                buildString {
                    append(
                        if (sdkInstanceName == null) {
                            "Default SDK instance"
                        } else {
                            "SDK instance with name=$sdkInstanceName"
                        }
                    )
                    append(" for ").append(networkingLibraryName).append(" instrumentation is not found")
                    if (url != null) append(", skipping tracking of request with url=").append(url)
                }
            }
        }

        return internalSdkCore
    }

    internal companion object {
        internal const val ZERO_SAMPLE_RATE: Float = 0.0f
        internal const val ALL_IN_SAMPLE_RATE: Double = 100.0
        internal const val SPAN_NAME = "%s.request"
        internal const val RESOURCE_NAME_404 = "404"
        internal const val AGENT_PSR_ATTRIBUTE = "_dd.agent_psr"
        internal const val URL_QUERY_PARAMS_BLOCK_SEPARATOR = '?'
        internal const val WARNING_TRACING_NO_HOSTS =
            "You added a ApmNetworkInstrumentation to your %s instrumentation, " +
                "but you did not specify any first party hosts. " +
                "Your requests won't be traced.\n" +
                "To set a list of known hosts, you can use the " +
                "Configuration.Builder.setFirstPartyHosts() method."
        internal const val ERROR_STACK_OVERFLOW =
            "StackOverflowError detected in TracedRequestListener. " +
                "This is likely caused by retrying the same request within the " +
                "onRequestIntercepted callback, leading to infinite recursion."

        internal const val ERROR_REQUEST_INFO_IS_NOT_MUTABLE =
            "HttpRequestInfo is not mutable. Your requests won't be traced."

        private fun HttpRequestInfo.newBuilder(internalLogger: InternalLogger): HttpRequestInfoBuilder? {
            return if (this is MutableHttpRequestInfo) {
                newBuilder()
            } else {
                internalLogger.log(
                    level = InternalLogger.Level.ERROR,
                    target = InternalLogger.Target.MAINTAINER,
                    messageBuilder = { ERROR_REQUEST_INFO_IS_NOT_MUTABLE }
                )
                null
            }
        }
    }
}
