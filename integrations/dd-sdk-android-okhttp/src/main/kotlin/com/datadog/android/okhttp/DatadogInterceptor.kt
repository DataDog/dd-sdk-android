/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.sampling.Sampler
import com.datadog.android.okhttp.internal.rum.NoOpRumResourceAttributesProvider
import com.datadog.android.okhttp.internal.rum.buildResourceId
import com.datadog.android.okhttp.internal.utils.traceIdAsHexString
import com.datadog.android.okhttp.trace.TracedRequestListener
import com.datadog.android.okhttp.trace.TracingInterceptor
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumResourceAttributesProvider
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.internal.monitor.AdvancedNetworkRumMonitor
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import com.datadog.android.trace.AndroidTracer
import com.datadog.android.trace.TracingHeaderType
import io.opentracing.Span
import io.opentracing.Tracer
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException
import java.util.Locale

/**
 * Provides automatic RUM & APM integration for [OkHttpClient] by way of the [Interceptor] system.
 *
 * For RUM integration: this interceptor will log the request as a RUM Resource, and fill the
 * request information (url, method, status code, optional error). Note that RUM Resources are only
 * tracked when a view is active. You can use one of the existing [ViewTrackingStrategy] when
 * configuring the SDK (see [RumConfiguration.Builder.useViewTrackingStrategy]) or start a view
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
 *    val tracedHostsWithHeaderType = mapOf("example.com" to setOf(
 *                 TracingHeaderType.DATADOG,
 *                 TracingHeaderType.TRACECONTEXT),
 *             "example.eu" to  setOf(
 *                 TracingHeaderType.DATADOG,
 *                 TracingHeaderType.TRACECONTEXT))
 *     val client = OkHttpClient.Builder()
 *         .addInterceptor(DatadogInterceptor.Builder(tracedHostsWithHeaderType).build())
 *         // Optionally to get information about redirections and retries
 *         // .addNetworkInterceptor(TracingInterceptor.Builder(tracedHostsWithHeaderType).build())
 *         .build()
 * ```
 */
open class DatadogInterceptor internal constructor(
    sdkInstanceName: String?,
    tracedHosts: Map<String, Set<TracingHeaderType>>,
    tracedRequestListener: TracedRequestListener,
    internal val rumResourceAttributesProvider: RumResourceAttributesProvider,
    traceSampler: Sampler<Span>,
    traceContextInjection: TraceContextInjection,
    redacted404ResourceName: Boolean,
    localTracerFactory: (SdkCore, Set<TracingHeaderType>) -> Tracer
) : TracingInterceptor(
    sdkInstanceName,
    tracedHosts,
    tracedRequestListener,
    ORIGIN_RUM,
    traceSampler,
    traceContextInjection,
    redacted404ResourceName,
    localTracerFactory
) {

    // region Interceptor

    /** @inheritdoc */
    override fun intercept(chain: Interceptor.Chain): Response {
        val sdkCore = sdkCoreReference.get() as? FeatureSdkCore
        val rumFeature = sdkCore?.getFeature(Feature.RUM_FEATURE_NAME)
        if (rumFeature != null) {
            val request = chain.request()
            val url = request.url.toString()
            val method = toHttpMethod(request.method, sdkCore.internalLogger)
            val requestId = request.buildResourceId(generateUuid = true)

            (GlobalRumMonitor.get(sdkCore) as? AdvancedNetworkRumMonitor)?.startResource(requestId, method, url)
        } else {
            val prefix = if (sdkInstanceName == null) {
                "Default SDK instance"
            } else {
                "SDK instance with name=$sdkInstanceName"
            }
            (sdkCore?.internalLogger ?: InternalLogger.UNBOUND).log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                { WARN_RUM_DISABLED.format(Locale.US, prefix) }
            )
        }
        return super.intercept(chain)
    }

    // endregion

    // region TracingInterceptor

    /** @inheritdoc */
    override fun onRequestIntercepted(
        sdkCore: FeatureSdkCore,
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
                handleThrowable(
                    sdkCore,
                    request,
                    throwable ?: IllegalStateException(ERROR_NO_RESPONSE)
                )
            }
        }
    }

    /** @inheritdoc */
    override fun canSendSpan(): Boolean {
        val rumFeature = (sdkCoreReference.get() as? FeatureSdkCore)
            ?.getFeature(Feature.RUM_FEATURE_NAME)
        return rumFeature == null
    }

    override fun onSdkInstanceReady(sdkCore: InternalSdkCore) {
        super.onSdkInstanceReady(sdkCore)
        (GlobalRumMonitor.get(sdkCore) as? AdvancedNetworkRumMonitor)?.notifyInterceptorInstantiated()
    }

    // endregion

    // region Internal

    private fun handleResponse(
        sdkCore: FeatureSdkCore,
        request: Request,
        response: Response,
        span: Span?,
        isSampled: Boolean
    ) {
        val requestId = request.buildResourceId(generateUuid = false)
        val statusCode = response.code
        val kind = when (val mimeType = response.header(HEADER_CT)) {
            null -> RumResourceKind.NATIVE
            else -> RumResourceKind.fromMimeType(mimeType)
        }
        val attributes = if (!isSampled || span == null) {
            emptyMap<String, Any?>()
        } else {
            mapOf(
                RumAttributes.TRACE_ID to span.context().traceIdAsHexString(),
                RumAttributes.SPAN_ID to span.context().toSpanId(),
                RumAttributes.RULE_PSR to (traceSampler.getSampleRate() ?: ZERO_SAMPLE_RATE) / ALL_IN_SAMPLE_RATE
            )
        }
        (GlobalRumMonitor.get(sdkCore) as? AdvancedNetworkRumMonitor)?.stopResource(
            requestId,
            statusCode,
            getBodyLength(response, sdkCore.internalLogger),
            kind,
            attributes + rumResourceAttributesProvider.onProvideAttributes(request, response, null)
        )
    }

    private fun handleThrowable(
        sdkCore: SdkCore,
        request: Request,
        throwable: Throwable
    ) {
        val requestId = request.buildResourceId(generateUuid = false)
        val method = request.method
        val url = request.url.toString()
        (GlobalRumMonitor.get(sdkCore) as? AdvancedNetworkRumMonitor)?.stopResourceWithError(
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
            val body = response.body
            val contentType = body?.contentType()?.let {
                // manually rebuild the mimetype as `toString()` can also include the charsets
                it.type + "/" + it.subtype
            }
            val isStream = contentType in STREAM_CONTENT_TYPES
            val isWebSocket = !response.header(WEBSOCKET_ACCEPT_HEADER, null).isNullOrBlank()
            if (body == null || isStream || isWebSocket) {
                return null
            }
            // if there is a Content-Length available, we can read it directly
            // however, OkHttp will drop Content-Length header if transparent compression is
            // used (since the value reported cannot be applied to decompressed body), so to be
            // able to still read it, we force decompression by calling peekBody
            body.contentLengthOrNull() ?: response.peekBody(MAX_BODY_PEEK).contentLengthOrNull()
        } catch (e: IOException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { ERROR_PEEK_BODY },
                e
            )
            null
        } catch (e: IllegalStateException) {
            // this happens if we cannot read body at all (ex. WebSocket, etc.), no need to report to telemetry
            internalLogger.log(
                InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                { ERROR_PEEK_BODY },
                e
            )
            null
        } catch (e: IllegalArgumentException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { ERROR_PEEK_BODY },
                e
            )
            null
        }
    }

    private fun toHttpMethod(method: String, internalLogger: InternalLogger): RumResourceMethod {
        return when (method.uppercase(Locale.US)) {
            "GET" -> RumResourceMethod.GET
            "PUT" -> RumResourceMethod.PUT
            "PATCH" -> RumResourceMethod.PATCH
            "HEAD" -> RumResourceMethod.HEAD
            "DELETE" -> RumResourceMethod.DELETE
            "POST" -> RumResourceMethod.POST
            "TRACE" -> RumResourceMethod.TRACE
            "OPTIONS" -> RumResourceMethod.OPTIONS
            "CONNECT" -> RumResourceMethod.CONNECT
            else -> {
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                    { UNSUPPORTED_HTTP_METHOD.format(Locale.US, method) }
                )
                RumResourceMethod.GET
            }
        }
    }

    private fun ResponseBody.contentLengthOrNull(): Long? {
        return contentLength().let {
            if (it < 0L) null else it
        }
    }

    // endregion

    // region Builder

    /**
     * A Builder for the [DatadogInterceptor].
     * @param tracedHostsWithHeaderType a list of all the hosts and header types that you want to
     * be automatically tracked by this interceptor. If registering a [io.opentracing.util.GlobalTracer],
     * the tracer must be configured with [AndroidTracer.Builder.setTracingHeaderTypes] containing all the necessary
     * header types configured for OkHttp tracking.
     * If no hosts are provided (via this argument or global configuration
     * [Configuration.Builder.setFirstPartyHosts] or [Configuration.Builder.setFirstPartyHostsWithHeaderType] )
     * the interceptor won't trace any OkHttp [Request], nor propagate tracing information to the backend.
     */
    class Builder(tracedHostsWithHeaderType: Map<String, Set<TracingHeaderType>>) :
        BaseBuilder<DatadogInterceptor, Builder>(tracedHostsWithHeaderType) {

        private var rumResourceAttributesProvider: RumResourceAttributesProvider = NoOpRumResourceAttributesProvider()

        constructor(tracedHosts: List<String>) : this(
            tracedHosts.associateWith {
                setOf(
                    TracingHeaderType.DATADOG,
                    TracingHeaderType.TRACECONTEXT
                )
            }
        )

        internal override fun getThis(): Builder {
            return this
        }

        /**
         * Builds the [DatadogInterceptor].
         */
        override fun build(): DatadogInterceptor {
            return DatadogInterceptor(
                sdkInstanceName,
                tracedHostsWithHeaderType,
                tracedRequestListener,
                rumResourceAttributesProvider,
                traceSampler,
                traceContextInjection,
                redacted404ResourceName,
                localTracerFactory
            )
        }

        /**
         * Sets the [RumResourceAttributesProvider] to use to provide custom attributes to the RUM.
         * By default it won't attach any custom attributes.
         * @param rumResourceAttributesProvider the [RumResourceAttributesProvider] to use.
         */
        fun setRumResourceAttributesProvider(rumResourceAttributesProvider: RumResourceAttributesProvider): Builder {
            this.rumResourceAttributesProvider = rumResourceAttributesProvider
            return this
        }
    }

    // endregion

    internal companion object {

        internal val STREAM_CONTENT_TYPES = setOf(
            "text/event-stream",
            "application/grpc",
            "application/grpc+proto",
            "application/grpc+json"
        )

        internal const val WEBSOCKET_ACCEPT_HEADER = "Sec-WebSocket-Accept"

        internal const val WARN_RUM_DISABLED =
            "You set up a DatadogInterceptor for %s, but RUM features are disabled. " +
                "Make sure you initialized the Datadog SDK with a valid Application Id, " +
                "and that RUM features are enabled."

        internal const val ERROR_NO_RESPONSE =
            "The request ended with no response nor any exception."

        internal const val ERROR_PEEK_BODY = "Unable to peek response body."

        internal const val ERROR_MSG_FORMAT = "OkHttp request error %s %s"

        internal const val UNSUPPORTED_HTTP_METHOD =
            "Unsupported HTTP method %s reported by OkHttp instrumentation, using GET instead"

        internal const val ORIGIN_RUM = "rum"

        // We need to limit this value as the body will be loaded in memory
        private const val MAX_BODY_PEEK: Long = 32 * 1024L * 1024L

        private const val ALL_IN_SAMPLE_RATE: Float = 100f
        private const val ZERO_SAMPLE_RATE: Float = 0f
    }
}
