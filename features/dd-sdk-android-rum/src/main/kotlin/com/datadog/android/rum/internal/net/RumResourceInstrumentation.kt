/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.instrumentation.network.RequestInfo
import com.datadog.android.api.instrumentation.network.ResponseInfo
import com.datadog.android.core.SdkReference
import com.datadog.android.core.internal.net.HttpSpec
import com.datadog.android.lint.InternalApi
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceAttributesProvider
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.monitor.AdvancedNetworkRumMonitor
import com.datadog.android.rum.resource.buildResourceId
import java.util.Locale

/**
 * Handles RUM (Real User Monitoring) instrumentation for network resources.
 * This class provides methods to track HTTP requests and responses as RUM resources.
 *
 * This class operates as a middle layer between any specific library instrumentation and SDK core
 * components, making possible to share same logic between different networking libraries (OkHttp, Cronet, etc)
 *
 * @param sdkInstanceName the name of the SDK instance to use, or null for the default instance
 * @param networkLayerName the name of the network layer being instrumented (e.g., "OkHttp", "Cronet")
 * @param rumResourceAttributesProvider provider for custom attributes to attach to RUM resources
 */
@InternalApi
class RumResourceInstrumentation(
    internal val sdkInstanceName: String?,
    internal val networkLayerName: String,
    internal val rumResourceAttributesProvider: RumResourceAttributesProvider
) {
    private val sdkCoreReference = SdkReference(sdkInstanceName) {
        it.networkMonitor?.notifyInterceptorInstantiated()
    }

    /**
     * Sends an event to indicate that resource timing information is expected for this request.
     * @param requestInfo the request information
     */
    fun sendWaitForResourceTimingEvent(requestInfo: RequestInfo) = ifRumEnabled { sdkCore ->
        sdkCore.networkMonitor?.waitForResourceTiming(requestInfo.buildResourceId(generateUuid = true))
    }

    /**
     * Sends resource timing information for a request.
     * @param requestInfo the request information
     * @param resourceTiming the timing information to report
     */
    fun sendTiming(requestInfo: RequestInfo, resourceTiming: ResourceTiming) = ifRumEnabled { sdkCore ->
        sdkCore.networkMonitor?.addResourceTiming(
            requestInfo.buildResourceId(generateUuid = false),
            resourceTiming
        )
    }

    /**
     * Starts tracking a network resource.
     * @param requestInfo the request information
     */
    fun startResource(requestInfo: RequestInfo) = ifRumEnabled { sdkCore ->
        sdkCore.networkMonitor?.startResource(
            requestInfo.buildResourceId(generateUuid = true),
            requestInfo.toRumResourceMethod(sdkCore.internalLogger),
            requestInfo.url
        )
    }

    /**
     * Stops tracking a network resource with a successful response.
     * @param requestInfo the request information
     * @param responseInfo the response information
     */
    fun stopResource(requestInfo: RequestInfo, responseInfo: ResponseInfo) = ifRumEnabled { sdkCore ->
        sdkCore.networkMonitor?.stopResource(
            requestInfo.buildResourceId(generateUuid = false),
            responseInfo.statusCode,
            responseInfo.getBodyLength(internalLogger = sdkCore.internalLogger),
            responseInfo.getRumResourceKind(),
            rumResourceAttributesProvider.onProvideAttributes(requestInfo, responseInfo, null)
        )
    }

    /**
     * Stops tracking a network resource with an error.
     * @param requestInfo the request information
     * @param throwable the error that occurred
     */
    fun stopResourceWithError(requestInfo: RequestInfo, throwable: Throwable) = ifRumEnabled { sdkCore ->
        sdkCore.networkMonitor?.stopResourceWithError(
            requestInfo.buildResourceId(generateUuid = false),
            null,
            ERROR_MSG_FORMAT.format(Locale.US, networkLayerName, requestInfo.method, requestInfo.url),
            RumErrorSource.NETWORK,
            throwable,
            rumResourceAttributesProvider.onProvideAttributes(requestInfo, null, throwable)
        )
    }

    /**
     * Reports an instrumentation error to the internal logger.
     * @param message the error message to report
     */
    fun reportInstrumentationError(message: String) = ifRumEnabled { sdkCore ->
        sdkCore.internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            { "Unable to instrument RUM resource: $message" }
        )
    }

    private fun ifRumEnabled(block: (FeatureSdkCore) -> Unit) {
        val sdkCore = sdkCoreReference.get() as? FeatureSdkCore
        val rumFeature = sdkCore?.getFeature(Feature.RUM_FEATURE_NAME)
        if (rumFeature != null) {
            block(sdkCore)
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
    }

    companion object {
        internal const val ERROR_MSG_FORMAT = "%s request error %s %s"
        internal const val UNSUPPORTED_HTTP_METHOD =
            "Unsupported HTTP method %s reported by OkHttp instrumentation, using GET instead"

        internal const val WARN_RUM_DISABLED =
            "You set up a DatadogInterceptor for %s, but RUM features are disabled. " +
                "Make sure you initialized the Datadog SDK with a valid Application Id, " +
                "and that RUM features are enabled."
        private fun RequestInfo.toRumResourceMethod(internalLogger: InternalLogger) =
            when (method) {
                HttpSpec.Method.GET -> RumResourceMethod.GET
                HttpSpec.Method.PUT -> RumResourceMethod.PUT
                HttpSpec.Method.PATCH -> RumResourceMethod.PATCH
                HttpSpec.Method.HEAD -> RumResourceMethod.HEAD
                HttpSpec.Method.DELETE -> RumResourceMethod.DELETE
                HttpSpec.Method.POST -> RumResourceMethod.POST
                HttpSpec.Method.TRACE -> RumResourceMethod.TRACE
                HttpSpec.Method.OPTIONS -> RumResourceMethod.OPTIONS
                HttpSpec.Method.CONNECT -> RumResourceMethod.CONNECT
                else -> {
                    internalLogger.log(
                        InternalLogger.Level.WARN,
                        targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                        { UNSUPPORTED_HTTP_METHOD.format(Locale.US, method) }
                    )
                    RumResourceMethod.GET
                }
            }

        private fun ResponseInfo.getRumResourceKind() =
            when (val mimeType = contentType) {
                null -> RumResourceKind.NATIVE
                else -> RumResourceKind.fromMimeType(mimeType)
            }

        private fun ResponseInfo.getBodyLength(internalLogger: InternalLogger): Long? {
            val isStream = HttpSpec.ContentType.isStream(contentType)
            val isWebSocket = !headers[HttpSpec.Headers.WEBSOCKET_ACCEPT_HEADER].isNullOrEmpty()
            return if (isStream || isWebSocket) null else computeContentLength(internalLogger)
        }

        private val SdkCore.networkMonitor: AdvancedNetworkRumMonitor?
            get() = (GlobalRumMonitor.get(this) as? AdvancedNetworkRumMonitor)
    }
}
