/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import com.datadog.android.core.internal.net.identifyRequest
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumInterceptor
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import com.datadog.android.tracing.AndroidTracer
import com.datadog.android.tracing.NoOpTracedRequestListener
import com.datadog.android.tracing.TracedRequestListener
import com.datadog.android.tracing.TracingInterceptor
import io.opentracing.Span
import io.opentracing.Tracer
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Provides automatic integration for [OkHttpClient] by way of the [Interceptor] system.
 *
 * This interceptor will combine the effects of the [TracingInterceptor] and the
 * [RumInterceptor].
 *
 * From [RumInterceptor]: this interceptor will log the request as a RUM Resource, and fill the
 * request information (url, method, status code, optional error). Note that RUM Resources are only
 * tracked when a view is active. You can use one of the existing [ViewTrackingStrategy] when
 * configuring the SDK (see [DatadogConfig.Builder.useViewTrackingStrategy]) or start a view
 * manually (see [RumMonitor.startView]).
 *
 * From [TracingInterceptor]: This interceptor will create a [Span] around the request and fill the
 * request information (url, method, status code, optional error). It will also propagate the span
 * and trace information in the request header to link it with backend spans.
 *
 * Note: If you want to get more insights on the network requests (such as redirections), you can also add
 * this interceptor as a Network level interceptor.
 *
 * To use:
 * ```
 *     val tracedHosts = listOf("example.com", "example.eu")
 *     OkHttpClient client = new OkHttpClient.Builder()
 *         .addInterceptor(new DatadogInterceptor(tracedHosts))
 *         // Optionally to get information about redirections and retries
 *         // .addNetworkInterceptor(new TracingInterceptor(tracedHosts))
 *         .build();
 * ```
 *
 * @param tracedHosts a list of all the hosts that you want to be automatically tracked
 * by our APM [TracingInterceptor]. If no host provided the interceptor won't trace
 * any OkHttpRequest, nor propagate tracing information to the backend.
 * Please note that the host constraint will only be applied on the [TracingInterceptor] and we will
 * continue to dispatch RUM Resource events for each request without applying any host filtering.
 * @param tracedRequestListener which listens on the intercepted [okhttp3.Request] and offers
 * the possibility to modify the created [io.opentracing.Span].
 *
 */
open class DatadogInterceptor
internal constructor(
    tracedHosts: List<String>,
    tracedRequestListener: TracedRequestListener = NoOpTracedRequestListener(),
    localTracerFactory: () -> Tracer
) : TracingInterceptor(tracedHosts, tracedRequestListener, localTracerFactory) {

    /**
     * Creates a [TracingInterceptor] to automatically create a trace around OkHttp [Request]s, and
     * track RUM Resources.
     *
     * @param tracedHosts a list of all the hosts that you want to be automatically tracked
     * by our APM [TracingInterceptor]. If no host provided the interceptor won't trace
     * any OkHttp [Request], nor propagate tracing information to the backend.
     * Please note that the host constraint will only be applied on the [TracingInterceptor] and we
     * will continue to dispatch RUM Resource events for each request without applying any host
     * filtering.
     * @param tracedRequestListener which listens on the intercepted [okhttp3.Request] and offers
     * the possibility to modify the created [io.opentracing.Span].
     */
    @JvmOverloads
    constructor(
        tracedHosts: List<String>,
        tracedRequestListener: TracedRequestListener = NoOpTracedRequestListener()
    ) : this(tracedHosts, tracedRequestListener, { AndroidTracer.Builder().build() })

    // region Interceptor

    /** @inheritdoc */
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url().toString()
        val method = request.method()
        val requestId = identifyRequest(request)

        GlobalRum.get().startResource(requestId, method, url)

        return super.intercept(chain)
    }

    // endregion

    // region TracingInterceptor

    /** @inheritdoc */
    override fun onRequestIntercepted(
        request: Request,
        span: Span,
        response: Response?,
        throwable: Throwable?
    ) {
        super.onRequestIntercepted(request, span, response, throwable)

        if (throwable != null) {
            handleThrowable(request, throwable)
        } else {
            handleResponse(request, response, span)
        }
    }

    // endregion

    // region Internal

    private fun handleResponse(
        request: Request,
        response: Response?,
        span: Span
    ) {
        val requestId = identifyRequest(request)
        val statusCode = response?.code()
        val method = request.method()
        val mimeType = response?.header(HEADER_CT)
        val kind = when {
            method in xhrMethods -> RumResourceKind.XHR
            mimeType == null -> RumResourceKind.UNKNOWN
            else -> RumResourceKind.fromMimeType(mimeType)
        }
        GlobalRum.get().stopResource(
            requestId,
            statusCode,
            getBodyLength(response),
            kind,
            mapOf(
                RumAttributes.TRACE_ID to span.context().toTraceId()
            )
        )
    }

    private fun handleThrowable(
        request: Request,
        throwable: Throwable
    ) {
        val requestId = identifyRequest(request)
        val method = request.method()
        val url = request.url().toString()
        GlobalRum.get().stopResourceWithError(
            requestId,
            null,
            ERROR_MSG_FORMAT.format(method, url),
            RumErrorSource.NETWORK,
            throwable
        )
    }

    private fun getBodyLength(response: Response?): Long? {
        val body = response?.peekBody(MAX_BODY_PEEK)
        val contentLength = body?.contentLength()
        return if (contentLength == 0L) null else contentLength
    }

    // endregion

    companion object {

        internal const val ERROR_MSG_FORMAT = "OkHttp request error %s %s"
        internal val xhrMethods = arrayOf("POST", "PUT", "DELETE")

        // We need to limit this value as the body will be loaded in memory
        private const val MAX_BODY_PEEK: Long = 32 * 1024L * 1024L
    }
}
