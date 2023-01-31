/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing

import androidx.annotation.FloatRange
import com.datadog.android.Datadog
import com.datadog.android.DatadogInterceptor
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.HostsSanitizer
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.core.internal.sampling.RateBasedSampler
import com.datadog.android.core.internal.sampling.Sampler
import com.datadog.android.core.internal.utils.internalLogger
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.core.internal.utils.percent
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.core.DatadogCore
import com.datadog.opentracing.DDTracer
import com.datadog.trace.api.DDTags
import com.datadog.trace.api.interceptor.MutableSpan
import com.datadog.trace.api.sampling.PrioritySampling
import io.opentracing.Span
import io.opentracing.SpanContext
import io.opentracing.Tracer
import io.opentracing.propagation.Format
import io.opentracing.propagation.TextMapExtractAdapter
import io.opentracing.propagation.TextMapInject
import io.opentracing.tag.Tags
import io.opentracing.util.GlobalTracer
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicReference

/**
 * Provides automatic trace integration for [OkHttpClient] by way of the [Interceptor] system.
 *
 * This interceptor will create a [Span] around the request and fill the request information
 * (url, method, status code, optional error). It will also propagate the span and trace
 * information in the request header to link it with backend spans.
 *
 * If you use multiple Interceptors, make sure that this one is called first.
 * If you also want to track network requests as RUM Resources, use the
 * [DatadogInterceptor] instead, which combines the RUM and APM integrations.
 *
 * If you want to get more insights on the network requests (e.g.: redirections), you can also add
 * this interceptor as a Network level interceptor.
 *
 * To use:
 * ```
 *     val tracedHosts = listOf("example.com", "example.eu")
 *     val okHttpClient = OkHttpClient.Builder()
 *         .addInterceptor(TracingInterceptor(tracedHosts)))
 *         // Optionally to get information about redirections and retries
 *         // .addNetworkInterceptor(new TracingInterceptor(tracedHosts))
 *         .build();
 * ```
 */
@Suppress("TooManyFunctions", "StringLiteralDuplication")
open class TracingInterceptor
internal constructor(
    internal val tracedHosts: Map<String, Set<TracingHeaderType>>,
    internal val tracedRequestListener: TracedRequestListener,
    internal val firstPartyHostResolver: FirstPartyHostHeaderTypeResolver,
    internal val traceOrigin: String?,
    internal val traceSampler: Sampler,
    internal val localTracerFactory: (Set<TracingHeaderType>) -> Tracer
) : Interceptor {

    private val localTracerReference: AtomicReference<Tracer> = AtomicReference()
    private val sanitizedHosts = HostsSanitizer().sanitizeHosts(
        tracedHosts.keys.toList(),
        NETWORK_REQUESTS_TRACKING_FEATURE_NAME
    )

    private val localFirstPartyHostHeaderTypeResolver =
        FirstPartyHostHeaderTypeResolver(tracedHosts.filterKeys { sanitizedHosts.contains(it) })

    init {
        if (localFirstPartyHostHeaderTypeResolver.isEmpty() && firstPartyHostResolver.isEmpty()) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                WARNING_TRACING_NO_HOSTS
            )
        }
    }

    /**
     * Creates a [TracingInterceptor] to automatically create a trace around OkHttp [Request]s.
     *
     * @param tracedHosts a list of all the hosts that you want to be automatically tracked
     * by this interceptor with Datadog style headers. If no host is provided (via this argument or global
     * configuration [Configuration.Builder.setFirstPartyHosts]) the interceptor won't trace any OkHttp [Request],
     * nor propagate tracing information to the backend.
     * @param tracedRequestListener a listener for automatically created [Span]s
     * @param traceSamplingRate the sampling rate for APM traces created for auto-instrumented
     * requests. It must be a value between `0.0` and `100.0`. A value of `0.0` means no trace will
     * be kept, `100.0` means all traces will be kept (default value is `20.0`).
     */
    @JvmOverloads
    constructor(
        tracedHosts: List<String>,
        tracedRequestListener: TracedRequestListener = NoOpTracedRequestListener(),
        @FloatRange(from = 0.0, to = 100.0) traceSamplingRate: Float = DEFAULT_TRACE_SAMPLING_RATE
    ) : this(
        tracedHosts.associateWith { setOf(TracingHeaderType.DATADOG) },
        tracedRequestListener,
        getGlobalFirstPartyHostResolver(),
        null,
        RateBasedSampler(traceSamplingRate.percent()),
        { AndroidTracer.Builder().setTracingHeaderTypes(it).build() }
    )

    /**
     * Creates a [TracingInterceptor] to automatically create a trace around OkHttp [Request]s.
     *
     * @param tracedHostsWithHeaderType a list of all the hosts and header types that you want to be automatically tracked
     * by this interceptor. If registering a GlobalTracer, the tracer must be configured with
     * [AndroidTracer.Builder.setTracingHeaderTypes] containing all the necessary header types configured for OkHttp tracking.
     * If no hosts are provided (via this argument or global
     * configuration [Configuration.Builder.setFirstPartyHosts] or [Configuration.Builder.setFirstPartyHostsWithHeaderType] )
     * the interceptor won't trace any OkHttp [Request], nor propagate tracing information to the backend.
     * @param tracedRequestListener a listener for automatically created [Span]s
     * @param traceSamplingRate the sampling rate for APM traces created for auto-instrumented
     * requests. It must be a value between `0.0` and `100.0`. A value of `0.0` means no trace will
     * be kept, `100.0` means all traces will be kept (default value is `20.0`).
     */
    @JvmOverloads
    constructor(
        tracedHostsWithHeaderType: Map<String, Set<TracingHeaderType>>,
        tracedRequestListener: TracedRequestListener = NoOpTracedRequestListener(),
        @FloatRange(from = 0.0, to = 100.0) traceSamplingRate: Float = DEFAULT_TRACE_SAMPLING_RATE
    ) : this(
        tracedHostsWithHeaderType,
        tracedRequestListener,
        getGlobalFirstPartyHostResolver(),
        null,
        RateBasedSampler(traceSamplingRate.percent()),
        { AndroidTracer.Builder().setTracingHeaderTypes(it).build() }
    )

    /**
     * Creates a [TracingInterceptor] to automatically create a trace around OkHttp [Request]s.
     *
     * @param tracedRequestListener a listener for automatically created [Span]s
     * @param traceSamplingRate the sampling rate for APM traces created for auto-instrumented
     * requests. It must be a value between `0.0` and `100.0`. A value of `0.0` means no trace will
     * be kept, `100.0` means all traces will be kept (default value is `20.0`).
     */
    @JvmOverloads
    constructor(
        tracedRequestListener: TracedRequestListener = NoOpTracedRequestListener(),
        @FloatRange(from = 0.0, to = 100.0) traceSamplingRate: Float = DEFAULT_TRACE_SAMPLING_RATE
    ) : this(
        emptyMap(),
        tracedRequestListener,
        getGlobalFirstPartyHostResolver(),
        null,
        RateBasedSampler(traceSamplingRate.percent()),
        { AndroidTracer.Builder().setTracingHeaderTypes(it).build() }
    )

    // region Interceptor

    /** @inheritdoc */
    override fun intercept(chain: Interceptor.Chain): Response {
        val tracer = resolveTracer()
        val request = chain.request()

        return if (tracer == null || !isRequestTraceable(request)) {
            intercept(chain, request)
        } else {
            interceptAndTrace(chain, request, tracer)
        }
    }

    // endregion

    // region TracingInterceptor

    /**
     * Called whenever a span was successfully created around an OkHttp [Request].
     * The given [Span] can be updated (e.g.: add custom tags / baggage items) before it is
     * finalized.
     * @param request the intercepted [Request]
     * @param span the [Span] created around the [Request] (or null if request is not traced)
     * @param response the [Request] response (or null if an error occurred)
     * @param throwable the error which occurred during the [Request] (or null)
     */
    protected open fun onRequestIntercepted(
        request: Request,
        span: Span?,
        response: Response?,
        throwable: Throwable?
    ) {
        if (span != null) {
            tracedRequestListener.onRequestIntercepted(request, span, response, throwable)
        }
    }

    /**
     * @return whether the span can be sent to Datadog.
     */
    internal open fun canSendSpan(): Boolean {
        return true
    }

    // endregion

    // region Internal

    private fun isRequestTraceable(request: Request): Boolean {
        val url = request.url()
        return firstPartyHostResolver.isFirstPartyUrl(url) ||
            localFirstPartyHostHeaderTypeResolver.isFirstPartyUrl(url)
    }

    @Suppress("TooGenericExceptionCaught", "ThrowingInternalException")
    private fun interceptAndTrace(
        chain: Interceptor.Chain,
        request: Request,
        tracer: Tracer
    ): Response {
        val isSampled = extractSamplingDecision(request) ?: traceSampler.sample()
        val span = buildSpan(tracer, request)

        val updatedRequest = try {
            updateRequest(request, tracer, span, isSampled).build()
        } catch (e: IllegalStateException) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                "Failed to update intercepted OkHttp request",
                e
            )
            request
        }

        try {
            val response = chain.proceed(updatedRequest)
            handleResponse(request, response, span, isSampled)
            return response
        } catch (e: Throwable) {
            handleThrowable(request, e, span, isSampled)
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught", "ThrowingInternalException")
    private fun intercept(
        chain: Interceptor.Chain,
        request: Request
    ): Response {
        try {
            val response = chain.proceed(request)
            onRequestIntercepted(request, null, response, null)
            return response
        } catch (e: Throwable) {
            onRequestIntercepted(request, null, null, e)
            throw e
        }
    }

    @Synchronized
    private fun resolveTracer(): Tracer? {
        val tracingFeature = (Datadog.globalSdkCore as? DatadogCore)?.tracingFeature
        return if (tracingFeature == null) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                WARNING_TRACING_DISABLED
            )
            null
        } else if (GlobalTracer.isRegistered()) {
            // clear the localTracer reference if any
            localTracerReference.set(null)
            GlobalTracer.get()
        } else {
            // we check if we already have a local tracer if not we instantiate one
            resolveLocalTracer()
        }
    }

    private fun resolveLocalTracer(): Tracer {
        // only register once
        if (localTracerReference.get() == null) {
            @Suppress("UnsafeThirdPartyFunctionCall") // internal safe call
            val localHeaderTypes = localFirstPartyHostHeaderTypeResolver.getAllHeaderTypes()
            val globalHeaderTypes = firstPartyHostResolver.getAllHeaderTypes()
            val allHeaders = localHeaderTypes.plus(globalHeaderTypes)
            localTracerReference.compareAndSet(null, localTracerFactory(allHeaders))
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                WARNING_DEFAULT_TRACER
            )
        }
        return localTracerReference.get()
    }

    private fun buildSpan(tracer: Tracer, request: Request): Span {
        val parentContext = extractParentContext(tracer, request)
        val url = request.url().toString()

        val spanBuilder = tracer.buildSpan(SPAN_NAME)
        (spanBuilder as? DDTracer.DDSpanBuilder)?.withOrigin(traceOrigin)
        val span = spanBuilder
            .asChildOf(parentContext)
            .start()

        (span as? MutableSpan)?.resourceName = url.substringBefore(URL_QUERY_PARAMS_BLOCK_SEPARATOR)
        span.setTag(Tags.HTTP_URL.key, url)
        span.setTag(Tags.HTTP_METHOD.key, request.method())

        return span
    }

    @Suppress("ReturnCount")
    private fun extractSamplingDecision(request: Request): Boolean? {
        val datadogSamplingPriority =
            request.header(DATADOG_SAMPLING_PRIORITY_HEADER)?.toIntOrNull()
        if (datadogSamplingPriority != null) {
            if (datadogSamplingPriority == PrioritySampling.UNSET) return null
            return datadogSamplingPriority == PrioritySampling.USER_KEEP ||
                datadogSamplingPriority == PrioritySampling.SAMPLER_KEEP
        }
        val b3MSamplingPriority = request.header(B3M_SAMPLING_PRIORITY_KEY)
        if (b3MSamplingPriority != null) {
            return when (b3MSamplingPriority) {
                "1" -> true
                "0" -> false
                else -> null
            }
        }

        val b3HeaderValue = request.header(B3_HEADER_KEY)
        if (b3HeaderValue != null) {
            if (b3HeaderValue == "0") {
                return false
            }
            val b3HeaderParts = b3HeaderValue.split("-")
            if (b3HeaderParts.size >= B3_SAMPLING_DECISION_INDEX + 1) {
                return when (b3HeaderParts[B3_SAMPLING_DECISION_INDEX]) {
                    "1", "d" -> true
                    "0" -> false
                    else -> null
                }
            }
        }

        val w3cHeaderValue = request.header(W3C_TRACEPARENT_KEY)
        if (w3cHeaderValue != null) {
            val w3CHeaderParts = w3cHeaderValue.split("-")
            if (w3CHeaderParts.size >= W3C_SAMPLING_DECISION_INDEX + 1) {
                return when (w3CHeaderParts[W3C_SAMPLING_DECISION_INDEX].toIntOrNull()) {
                    1 -> true
                    0 -> false
                    else -> null
                }
            }
        }

        return null
    }

    private fun extractParentContext(tracer: Tracer, request: Request): SpanContext? {
        val tagContext = request.tag(Span::class.java)?.context()

        val headerContext = tracer.extract(
            Format.Builtin.TEXT_MAP_EXTRACT,
            TextMapExtractAdapter(
                request.headers().toMultimap()
                    .map { it.key to it.value.joinToString(";") }
                    .toMap()
            )
        )

        return headerContext ?: tagContext
    }

    private fun setSampledOutHeaders(
        requestBuilder: Request.Builder,
        tracingHeaderTypes: Set<TracingHeaderType>,
        span: Span
    ) {
        for (headerType in tracingHeaderTypes) {
            when (headerType) {
                TracingHeaderType.DATADOG -> {
                    listOf(
                        DATADOG_SAMPLING_PRIORITY_HEADER,
                        DATADOG_TRACE_ID_HEADER,
                        DATADOG_SPAN_ID_HEADER,
                        DATADOG_ORIGIN_HEADER
                    ).forEach {
                        requestBuilder.removeHeader(it)
                    }
                    requestBuilder.addHeader(
                        DATADOG_SAMPLING_PRIORITY_HEADER,
                        DATADOG_DROP_SAMPLING_DECISION
                    )
                }

                TracingHeaderType.B3 -> {
                    requestBuilder.removeHeader(B3_HEADER_KEY)
                    requestBuilder.addHeader(B3_HEADER_KEY, B3_DROP_SAMPLING_DECISION)
                }

                TracingHeaderType.B3MULTI -> {
                    listOf(
                        B3M_TRACE_ID_KEY,
                        B3M_SPAN_ID_KEY,
                        B3M_SAMPLING_PRIORITY_KEY
                    ).forEach {
                        requestBuilder.removeHeader(it)
                    }
                    requestBuilder.addHeader(B3M_SAMPLING_PRIORITY_KEY, B3M_DROP_SAMPLING_DECISION)
                }

                TracingHeaderType.TRACECONTEXT -> {
                    requestBuilder.removeHeader(W3C_TRACEPARENT_KEY)
                    requestBuilder.addHeader(
                        W3C_TRACEPARENT_KEY,
                        W3C_DROP_SAMPLING_DECISION.format(
                            span.context().toTraceId(),
                            span.context().toSpanId()
                        )
                    )
                }
            }
        }
    }

    private fun updateRequest(
        request: Request,
        tracer: Tracer,
        span: Span,
        isSampled: Boolean
    ): Request.Builder {
        val tracedRequestBuilder = request.newBuilder()
        var tracingHeaderTypes =
            localFirstPartyHostHeaderTypeResolver.headerTypesForUrl(request.url())
        tracingHeaderTypes = if (!tracingHeaderTypes.isEmpty()) {
            tracingHeaderTypes
        } else {
            firstPartyHostResolver.headerTypesForUrl(request.url())
        }

        if (!isSampled) {
            setSampledOutHeaders(tracedRequestBuilder, tracingHeaderTypes, span)
        } else {
            tracer.inject(
                span.context(),
                Format.Builtin.TEXT_MAP_INJECT,
                TextMapInject { key, value ->
                    // By default the `addHeader` method adds a value and doesn't replace it
                    // We need to remove the old trace/span info to use the one for the current span
                    tracedRequestBuilder.removeHeader(key)
                    when (key) {
                        DATADOG_SAMPLING_PRIORITY_HEADER,
                        DATADOG_TRACE_ID_HEADER,
                        DATADOG_SPAN_ID_HEADER,
                        DATADOG_ORIGIN_HEADER -> if (tracingHeaderTypes.contains(TracingHeaderType.DATADOG)) {
                            tracedRequestBuilder.addHeader(key, value)
                        }
                        B3_HEADER_KEY -> if (tracingHeaderTypes.contains(TracingHeaderType.B3)) {
                            tracedRequestBuilder.addHeader(key, value)
                        }
                        B3M_SPAN_ID_KEY,
                        B3M_TRACE_ID_KEY,
                        B3M_SAMPLING_PRIORITY_KEY -> if (tracingHeaderTypes.contains(
                                TracingHeaderType.B3MULTI
                            )
                        ) {
                            tracedRequestBuilder.addHeader(key, value)
                        }
                        W3C_TRACEPARENT_KEY -> if (tracingHeaderTypes.contains(TracingHeaderType.TRACECONTEXT)) {
                            tracedRequestBuilder.addHeader(key, value)
                        }
                        else -> tracedRequestBuilder.addHeader(key, value)
                    }
                }
            )
        }

        return tracedRequestBuilder
    }

    private fun handleResponse(
        request: Request,
        response: Response,
        span: Span?,
        isSampled: Boolean
    ) {
        if (!isSampled || span == null) {
            onRequestIntercepted(request, null, response, null)
        } else {
            val statusCode = response.code()
            span.setTag(Tags.HTTP_STATUS.key, statusCode)
            if (statusCode in HttpURLConnection.HTTP_BAD_REQUEST until HttpURLConnection.HTTP_INTERNAL_ERROR) {
                (span as? MutableSpan)?.isError = true
            }
            if (statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
                (span as? MutableSpan)?.resourceName = RESOURCE_NAME_404
            }
            onRequestIntercepted(request, span, response, null)
            if (canSendSpan()) {
                span.finish()
            } else {
                (span as? MutableSpan)?.drop()
            }
        }
    }

    private fun handleThrowable(
        request: Request,
        throwable: Throwable,
        span: Span?,
        isSampled: Boolean
    ) {
        if (!isSampled || span == null) {
            onRequestIntercepted(request, null, null, throwable)
        } else {
            (span as? MutableSpan)?.isError = true
            span.setTag(DDTags.ERROR_MSG, throwable.message)
            span.setTag(DDTags.ERROR_TYPE, throwable.javaClass.name)
            span.setTag(DDTags.ERROR_STACK, throwable.loggableStackTrace())
            onRequestIntercepted(request, span, null, throwable)
            if (canSendSpan()) {
                span.finish()
            } else {
                (span as? MutableSpan)?.drop()
            }
        }
    }

    // endregion

    internal companion object {
        internal const val SPAN_NAME = "okhttp.request"

        internal const val RESOURCE_NAME_404 = "404"

        internal const val HEADER_CT = "Content-Type"
        internal const val URL_QUERY_PARAMS_BLOCK_SEPARATOR = '?'

        internal const val WARNING_TRACING_NO_HOSTS =
            "You added a TracingInterceptor to your OkHttpClient, " +
                "but you did not specify any first party hosts. " +
                "Your requests won't be traced.\n" +
                "To set a list of known hosts, you can use the " +
                "Configuration.Builder::setFirstPartyHosts() method."
        internal const val WARNING_TRACING_DISABLED =
            "You added a TracingInterceptor to your OkHttpClient, " +
                "but you did not enable the TracingFeature. " +
                "Your requests won't be traced."
        internal const val WARNING_DEFAULT_TRACER =
            "You added a TracingInterceptor to your OkHttpClient, " +
                "but you didn't register any Tracer. " +
                "We automatically created a local tracer for you."

        /**
         * Temporary helper method for now, it'll be removed eventually.
         */
        @Suppress("FunctionMaxLength")
        internal fun getGlobalFirstPartyHostResolver(): FirstPartyHostHeaderTypeResolver {
            return (
                (Datadog.globalSdkCore as? DatadogCore)
                    ?.coreFeature
                    ?.firstPartyHostHeaderTypeResolver
                    ?: FirstPartyHostHeaderTypeResolver(emptyMap())
                )
        }

        internal const val NETWORK_REQUESTS_TRACKING_FEATURE_NAME = "Network Requests"
        internal const val DEFAULT_TRACE_SAMPLING_RATE: Float = 20f

        // taken from DatadogHttpCodec
        internal const val DATADOG_TRACE_ID_HEADER = "x-datadog-trace-id"
        internal const val DATADOG_SPAN_ID_HEADER = "x-datadog-parent-id"
        internal const val DATADOG_SAMPLING_PRIORITY_HEADER = "x-datadog-sampling-priority"
        internal const val DATADOG_DROP_SAMPLING_DECISION = "0"
        internal const val DATADOG_ORIGIN_HEADER = "x-datadog-origin"

        // taken from B3HttpCodec
        internal const val B3_HEADER_KEY = "b3"
        internal const val B3_DROP_SAMPLING_DECISION = "0"
        internal const val B3_SAMPLING_DECISION_INDEX = 2

        // taken from B3MHttpCodec
        internal const val B3M_TRACE_ID_KEY = "X-B3-TraceId"
        internal const val B3M_SPAN_ID_KEY = "X-B3-SpanId"
        internal const val B3M_SAMPLING_PRIORITY_KEY = "X-B3-Sampled"
        internal const val B3M_DROP_SAMPLING_DECISION = "0"

        // taken from W3CHttpCodec
        internal const val W3C_TRACEPARENT_KEY = "traceparent"
        internal const val W3C_DROP_SAMPLING_DECISION = "00-%s-%s-00"
        internal const val W3C_SAMPLING_DECISION_INDEX = 3
    }
}
