/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing

import com.datadog.android.DatadogInterceptor
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.net.FirstPartyHostDetector
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.tracing.internal.TracesFeature
import com.datadog.trace.api.DDTags
import com.datadog.trace.api.interceptor.MutableSpan
import io.opentracing.Span
import io.opentracing.SpanContext
import io.opentracing.Tracer
import io.opentracing.propagation.Format
import io.opentracing.propagation.TextMapExtractAdapter
import io.opentracing.propagation.TextMapInject
import io.opentracing.tag.Tags
import io.opentracing.util.GlobalTracer
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

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
 *
 * @param tracedHosts a list of all the hosts that you want to be automatically tracked
 * by this interceptor. If no host is provided the interceptor won't trace any OkHttpRequest,
 * nor propagate tracing information to the backend.
 * @param tracedRequestListener a listener for automatically created [Span]s
 *
 */
@Suppress("StringLiteralDuplication")
open class TracingInterceptor
@JvmOverloads internal constructor(
    @Deprecated("hosts should be defined in the DatadogConfig.setFirstPartyHosts()")
    internal val tracedHosts: List<String>,
    internal val tracedRequestListener: TracedRequestListener,
    internal val firstPartyHostDetector: FirstPartyHostDetector,
    internal val localTracerFactory: () -> Tracer
) : Interceptor {

    private val localTracerReference: AtomicReference<Tracer> = AtomicReference()

    init {
        if (tracedHosts.isEmpty() && firstPartyHostDetector.isEmpty()) {
            devLogger.w(WARNING_TRACING_NO_HOSTS)
        }
        firstPartyHostDetector.addKnownHosts(tracedHosts)
    }

    /**
     * Creates a [TracingInterceptor] to automatically create a trace around OkHttp [Request]s.
     *
     * @param tracedHosts a list of all the hosts that you want to be automatically tracked
     * by this interceptor. If no host is provided the interceptor won't trace any OkHttp [Request],
     * nor propagate tracing information to the backend.
     * @param tracedRequestListener a listener for automatically created [Span]s
     */
    @JvmOverloads
    @Deprecated("hosts should be defined in the DatadogConfig.setFirstPartyHosts()")
    constructor(
        tracedHosts: List<String>,
        tracedRequestListener: TracedRequestListener = NoOpTracedRequestListener()
    ) : this(
        tracedHosts,
        tracedRequestListener,
        CoreFeature.firstPartyHostDetector,
        { AndroidTracer.Builder().build() }
    )

    /**
     * Creates a [TracingInterceptor] to automatically create a trace around OkHttp [Request]s.
     *
     * @param tracedRequestListener a listener for automatically created [Span]s
     */
    @JvmOverloads
    constructor(
        tracedRequestListener: TracedRequestListener = NoOpTracedRequestListener()
    ) : this(
        emptyList(),
        tracedRequestListener,
        CoreFeature.firstPartyHostDetector,
        { AndroidTracer.Builder().build() }
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

    // endregion

    // region Internal

    private fun isRequestTraceable(request: Request): Boolean {
        val url = request.url()
        return firstPartyHostDetector.isFirstPartyUrl(url)
    }

    @Suppress("TooGenericExceptionCaught", "ThrowingInternalException")
    private fun interceptAndTrace(
        chain: Interceptor.Chain,
        request: Request,
        tracer: Tracer
    ): Response {
        val span = buildSpan(tracer, request)
        val updatedRequest = updateRequest(request, tracer, span).build()

        try {
            val response = chain.proceed(updatedRequest)
            handleResponse(request, response, span)
            return response
        } catch (e: Throwable) {
            handleThrowable(request, e, span)
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
        return if (!TracesFeature.initialized.get()) {
            devLogger.w(WARNING_TRACING_DISABLED)
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
        if (localTracerReference.get() == null) {
            // only register once
            localTracerReference.compareAndSet(null, localTracerFactory())
            devLogger.w(WARNING_DEFAULT_TRACER)
        }
        return localTracerReference.get()
    }

    private fun buildSpan(tracer: Tracer, request: Request): Span {
        val parentContext = extractParentContext(tracer, request)
        val url = request.url().toString()

        val span = tracer.buildSpan(SPAN_NAME)
            .asChildOf(parentContext)
            .start()

        (span as? MutableSpan)?.resourceName = url
        span.setTag(Tags.HTTP_URL.key, url)
        span.setTag(Tags.HTTP_METHOD.key, request.method())

        return span
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

    private fun updateRequest(
        request: Request,
        tracer: Tracer,
        span: Span
    ): Request.Builder {
        val tracedRequestBuilder = request.newBuilder()

        tracer.inject(
            span.context(),
            Format.Builtin.TEXT_MAP_INJECT,
            TextMapInject { key, value ->
                tracedRequestBuilder.addHeader(key, value)
            }
        )

        return tracedRequestBuilder
    }

    private fun handleResponse(
        request: Request,
        response: Response,
        span: Span?
    ) {
        val statusCode = response.code()
        span?.setTag(Tags.HTTP_STATUS.key, statusCode)
        if (statusCode in 400..499) {
            (span as? MutableSpan)?.setError(true)
        }
        if (statusCode == 404) {
            (span as? MutableSpan)?.setResourceName(RESOURCE_NAME_404)
        }
        onRequestIntercepted(request, span, response, null)
        span?.finish()
    }

    private fun handleThrowable(
        request: Request,
        throwable: Throwable,
        span: Span
    ) {
        (span as? MutableSpan)?.setError(true)
        span.setTag(DDTags.ERROR_MSG, throwable.message)
        span.setTag(DDTags.ERROR_TYPE, throwable.javaClass.name)
        span.setTag(DDTags.ERROR_STACK, throwable.loggableStackTrace())
        onRequestIntercepted(request, span, null, throwable)
        span.finish()
    }

    // endregion

    companion object {
        internal const val SPAN_NAME = "okhttp.request"

        internal const val RESOURCE_NAME_404 = "404"

        internal const val HEADER_CT = "Content-Type"

        internal const val WARNING_TRACING_NO_HOSTS =
            "You added a TracingInterceptor to your OkHttpClient, " +
                "but you did not specify any first party hosts. " +
                "Your requests won't be traced.\n" +
                "To set a list of known hosts, you can use the " +
                "DatadogConfig.Builder::setFirstPartyHosts() method."
        internal const val WARNING_TRACING_DISABLED =
            "You added a TracingInterceptor to your OkHttpClient, " +
                "but you did not enable the TracesFeature. " +
                "Your requests won't be traced."
        internal const val WARNING_DEFAULT_TRACER =
            "You added a TracingInterceptor to your OkHttpClient, " +
                "but you didn't register any Tracer. " +
                "We automatically created a local tracer for you."
    }
}
