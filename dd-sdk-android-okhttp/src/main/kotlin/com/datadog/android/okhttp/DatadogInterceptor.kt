/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp

import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.sampling.RateBasedSampler
import com.datadog.android.core.sampling.Sampler
import com.datadog.android.okhttp.rum.NoOpRumResourceAttributesProvider
import com.datadog.android.okhttp.trace.NoOpTracedRequestListener
import com.datadog.android.okhttp.trace.TracedRequestListener
import com.datadog.android.okhttp.trace.TracingInterceptor
import com.datadog.android.okhttp.utils.identifyRequest
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumFeature
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumResourceAttributesProvider
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.internal.monitor.AdvancedNetworkRumMonitor
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import com.datadog.android.trace.AndroidTracer
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore
import io.opentracing.Span
import io.opentracing.Tracer
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.Locale

/**
 * Provides automatic RUM & APM integration for [OkHttpClient] by way of the [Interceptor] system.
 *
 * For RUM integration: this interceptor will log the request as a RUM Resource, and fill the
 * request information (url, method, status code, optional error). Note that RUM Resources are only
 * tracked when a view is active. You can use one of the existing [ViewTrackingStrategy] when
 * configuring the SDK (see [RumFeature.Builder.useViewTrackingStrategy]) or start a view
 * manually (see [RumMonitor.startView]).
 *
 * For APM integration: This interceptor will create a [Span] around the request and fill the
 * request information (url, method, status code, optional error). It will also propagate the span
 * and trace information in the request header to link it with backend spans.
 *
 * Note: If you want to get more insights on the network requests (such as redirections), you can also add
 * [TracingInterceptor] interceptor as a Network level interceptor.
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
 */
open class DatadogInterceptor
internal constructor(
    sdkInstanceName: String?,
    tracedHosts: Map<String, Set<TracingHeaderType>>,
    tracedRequestListener: TracedRequestListener,
    internal val rumResourceAttributesProvider: RumResourceAttributesProvider,
    traceSampler: Sampler,
    localTracerFactory: (SdkCore, Set<TracingHeaderType>) -> Tracer
) : TracingInterceptor(
    sdkInstanceName,
    tracedHosts,
    tracedRequestListener,
    ORIGIN_RUM,
    traceSampler,
    localTracerFactory
) {

    /**
     * Creates a [TracingInterceptor] to automatically create a trace around OkHttp [Request]s, and
     * track RUM Resources.
     *
     * @param sdkInstanceName SDK instance name to bind to, or null to check the default instance.
     * Instrumentation won't be working until SDK instance is ready.
     * @param firstPartyHostsWithHeaderType the list of all the hosts and header types that you want to
     * be automatically tracked by this interceptor.
     * Requests made to a URL with any one of these hosts (or any subdomain) will:
     * - be considered a first party RUM Resource and categorised as such in your RUM dashboard;
     * - be wrapped in a Span and have trace id injected to get a full flame-graph in APM.
     * If no host provided (via this argument, global configuration [Configuration.Builder.setFirstPartyHosts]
     * or [Configuration.Builder.setFirstPartyHostsWithHeaderType])
     * the interceptor won't trace any OkHttp [Request], nor propagate tracing
     * information to the backend, but RUM Resource events will still be sent for each request.
     * @param tracedRequestListener which listens on the intercepted [okhttp3.Request] and offers
     * the possibility to modify the created [io.opentracing.Span].
     * @param rumResourceAttributesProvider which listens on the intercepted [okhttp3.Request]
     * and offers the possibility to add custom attributes to the RUM resource events.
     * @param traceSampler Sampler controlling the sampling of APM traces created for
     * auto-instrumented requests. By default it is [RateBasedSampler], which either can accept
     * fixed sampling rate or can get it dynamically from the provider. Value between `0.0` and
     * `100.0`. A value of `0.0` means no trace will be kept, `100.0` means all traces will
     * be kept (default value is `20.0`).
     */
    @JvmOverloads
    constructor(
        sdkInstanceName: String? = null,
        firstPartyHostsWithHeaderType: Map<String, Set<TracingHeaderType>>,
        tracedRequestListener: TracedRequestListener = NoOpTracedRequestListener(),
        rumResourceAttributesProvider: RumResourceAttributesProvider =
            NoOpRumResourceAttributesProvider(),
        traceSampler: Sampler = RateBasedSampler(DEFAULT_TRACE_SAMPLING_RATE)
    ) : this(
        sdkInstanceName = sdkInstanceName,
        tracedHosts = firstPartyHostsWithHeaderType,
        tracedRequestListener = tracedRequestListener,
        rumResourceAttributesProvider = rumResourceAttributesProvider,
        traceSampler = traceSampler,
        localTracerFactory = { sdkCore, tracingHeaderTypes ->
            AndroidTracer.Builder(sdkCore).setTracingHeaderTypes(tracingHeaderTypes).build()
        }
    )

    /**
     * Creates a [DatadogInterceptor] to automatically create a trace around OkHttp [Request]s, and
     * track RUM Resources.
     *
     * @param sdkInstanceName SDK instance name to bind to, or null to check the default instance.
     * Instrumentation won't be working until SDK instance is ready.
     * @param firstPartyHosts the list of first party hosts.
     * Requests made to a URL with any one of these hosts (or any subdomain) will:
     * - be considered a first party RUM Resource and categorised as such in your RUM dashboard;
     * - be wrapped in a Span and have trace id injected to get a full flame-graph in APM.
     * If no host provided (via this argument, global configuration [Configuration.Builder.setFirstPartyHosts]
     * or [Configuration.Builder.setFirstPartyHostsWithHeaderType])
     * the interceptor won't trace any OkHttp [Request], nor propagate tracing
     * information to the backend, but RUM Resource events will still be sent for each request.
     * @param tracedRequestListener which listens on the intercepted [okhttp3.Request] and offers
     * the possibility to modify the created [io.opentracing.Span].
     * @param rumResourceAttributesProvider which listens on the intercepted [okhttp3.Request]
     * and offers the possibility to add custom attributes to the RUM resource events.
     * @param traceSampler Sampler controlling the sampling of APM traces created for
     * auto-instrumented requests. By default it is [RateBasedSampler], which either can accept
     * fixed sampling rate or can get it dynamically from the provider. Value between `0.0` and
     * `100.0`. A value of `0.0` means no trace will be kept, `100.0` means all traces will
     * be kept (default value is `20.0`).
     */
    @JvmOverloads
    constructor(
        sdkInstanceName: String? = null,
        firstPartyHosts: List<String>,
        tracedRequestListener: TracedRequestListener = NoOpTracedRequestListener(),
        rumResourceAttributesProvider: RumResourceAttributesProvider =
            NoOpRumResourceAttributesProvider(),
        traceSampler: Sampler = RateBasedSampler(DEFAULT_TRACE_SAMPLING_RATE)
    ) : this(
        sdkInstanceName = sdkInstanceName,
        tracedHosts = firstPartyHosts.associateWith { setOf(TracingHeaderType.DATADOG) },
        tracedRequestListener = tracedRequestListener,
        rumResourceAttributesProvider = rumResourceAttributesProvider,
        traceSampler = traceSampler,
        localTracerFactory = { sdkCore, tracingHeaderTypes ->
            AndroidTracer.Builder(sdkCore).setTracingHeaderTypes(tracingHeaderTypes).build()
        }
    )

    /**
     * Creates a [TracingInterceptor] to automatically create a trace around OkHttp [Request]s, and
     * track RUM Resources.
     *
     * @param sdkInstanceName SDK instance name to bind to, or null to check the default instance.
     * Instrumentation won't be working until SDK instance is ready.
     * @param tracedRequestListener which listens on the intercepted [okhttp3.Request] and offers
     * the possibility to modify the created [io.opentracing.Span].
     * @param rumResourceAttributesProvider which listens on the intercepted [okhttp3.Request]
     * and offers the possibility to add custom attributes to the RUM resource events.
     * @param traceSampler Sampler controlling the sampling of APM traces created for
     * auto-instrumented requests. By default it is [RateBasedSampler], which either can accept
     * fixed sampling rate or can get it dynamically from the provider. Value between `0.0` and
     * `100.0`. A value of `0.0` means no trace will be kept, `100.0` means all traces will
     * be kept (default value is `20.0`).
     */
    @JvmOverloads
    constructor(
        sdkInstanceName: String? = null,
        tracedRequestListener: TracedRequestListener = NoOpTracedRequestListener(),
        rumResourceAttributesProvider: RumResourceAttributesProvider =
            NoOpRumResourceAttributesProvider(),
        traceSampler: Sampler = RateBasedSampler(DEFAULT_TRACE_SAMPLING_RATE)
    ) : this(
        sdkInstanceName = sdkInstanceName,
        tracedHosts = emptyMap(),
        tracedRequestListener = tracedRequestListener,
        rumResourceAttributesProvider = rumResourceAttributesProvider,
        traceSampler = traceSampler,
        localTracerFactory = { sdkCore, tracingHeaderTypes ->
            AndroidTracer.Builder(sdkCore).setTracingHeaderTypes(tracingHeaderTypes).build()
        }
    )

    // region Interceptor

    /** @inheritdoc */
    override fun intercept(chain: Interceptor.Chain): Response {
        val sdkCore = sdkCoreReference.get()
        val rumFeature = sdkCore?.getFeature(Feature.RUM_FEATURE_NAME)
        if (rumFeature != null) {
            val request = chain.request()
            val url = request.url().toString()
            val method = request.method()
            val requestId = identifyRequest(request)

            GlobalRum.get(sdkCore).startResource(requestId, method, url)
        } else {
            sdkCore?._internalLogger?.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                WARN_RUM_DISABLED
            )
        }
        return super.intercept(chain)
    }

    // endregion

    // region TracingInterceptor

    /** @inheritdoc */
    override fun onRequestIntercepted(
        sdkCore: SdkCore,
        request: Request,
        span: Span?,
        response: Response?,
        throwable: Throwable?
    ) {
        super.onRequestIntercepted(sdkCore, request, span, response, throwable)
        val rumFeature = sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
        if (rumFeature != null) {
            if (response != null) {
                handleResponse(sdkCore, request, response, span, span != null)
            } else {
                handleThrowable(sdkCore, request, throwable ?: IllegalStateException(ERROR_NO_RESPONSE))
            }
        }
    }

    /** @inheritdoc */
    override fun canSendSpan(): Boolean {
        val rumFeature = sdkCoreReference.get()?.getFeature(Feature.RUM_FEATURE_NAME)
        return rumFeature == null
    }

    override fun onSdkInstanceReady(sdkCore: SdkCore) {
        super.onSdkInstanceReady(sdkCore)
        (GlobalRum.get(sdkCore) as? AdvancedNetworkRumMonitor)?.notifyInterceptorInstantiated()
    }

    // endregion

    // region Internal

    private fun handleResponse(
        sdkCore: SdkCore,
        request: Request,
        response: Response,
        span: Span?,
        isSampled: Boolean
    ) {
        val requestId = identifyRequest(request)
        val statusCode = response.code()
        val kind = when (val mimeType = response.header(HEADER_CT)) {
            null -> RumResourceKind.NATIVE
            else -> RumResourceKind.fromMimeType(mimeType)
        }
        val attributes = if (!isSampled || span == null) {
            emptyMap<String, Any?>()
        } else {
            mapOf(
                RumAttributes.TRACE_ID to span.context().toTraceId(),
                RumAttributes.SPAN_ID to span.context().toSpanId(),
                RumAttributes.RULE_PSR to traceSampler.getSamplingRate()
            )
        }
        GlobalRum.get(sdkCore).stopResource(
            requestId,
            statusCode,
            getBodyLength(response, sdkCore._internalLogger),
            kind,
            attributes + rumResourceAttributesProvider.onProvideAttributes(request, response, null)
        )
    }

    private fun handleThrowable(
        sdkCore: SdkCore,
        request: Request,
        throwable: Throwable
    ) {
        val requestId = identifyRequest(request)
        val method = request.method()
        val url = request.url().toString()
        GlobalRum.get(sdkCore).stopResourceWithError(
            requestId,
            null,
            ERROR_MSG_FORMAT.format(Locale.US, method, url),
            RumErrorSource.NETWORK,
            throwable,
            rumResourceAttributesProvider.onProvideAttributes(request, null, throwable)
        )
    }

    private fun getBodyLength(response: Response, internalLogger: InternalLogger): Long? {
        return try {
            val body = response.peekBody(MAX_BODY_PEEK)
            val contentLength = body.contentLength()
            if (contentLength == 0L) null else contentLength
        } catch (e: IOException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                ERROR_PEEK_BODY,
                e
            )
            null
        } catch (e: IllegalStateException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                ERROR_PEEK_BODY,
                e
            )
            null
        } catch (e: IllegalArgumentException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                ERROR_PEEK_BODY,
                e
            )
            null
        }
    }

    // endregion

    internal companion object {

        internal const val WARN_RUM_DISABLED =
            "You set up a DatadogInterceptor, but RUM features are disabled. " +
                "Make sure you initialized the Datadog SDK with a valid Application Id, " +
                "and that RUM features are enabled."

        internal const val ERROR_NO_RESPONSE =
            "The request ended with no response nor any exception."

        internal const val ERROR_PEEK_BODY = "Unable to peek response body."

        internal const val ERROR_MSG_FORMAT = "OkHttp request error %s %s"

        internal const val ORIGIN_RUM = "rum"

        // We need to limit this value as the body will be loaded in memory
        private const val MAX_BODY_PEEK: Long = 32 * 1024L * 1024L
    }
}
