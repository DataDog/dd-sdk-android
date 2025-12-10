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
import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.HttpResponseInfo
import com.datadog.android.api.instrumentation.network.tag
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
import com.datadog.android.rum.resource.ResourceId
import java.util.Locale
import java.util.UUID

/**
 * Handles RUM (Real User Monitoring) instrumentation for network resources.
 * This class provides methods to track HTTP requests and responses as RUM resources.
 *
 * This class operates as a middle layer between any specific library instrumentation and SDK core
 * components, making possible to share same logic between different networking libraries (OkHttp, Cronet, etc)
 *
 * @param sdkInstanceName the name of the SDK instance to use, or null for the default instance
 * @param networkInstrumentationName the name of the network layer being instrumented (e.g., "OkHttp", "Cronet")
 * @param rumResourceAttributesProvider provider for custom attributes to attach to RUM resources
 */
@InternalApi
class RumResourceInstrumentation(
    internal val sdkInstanceName: String?,
    internal val networkInstrumentationName: String,
    internal val rumResourceAttributesProvider: RumResourceAttributesProvider
) {
    private val sdkCoreReference = SdkReference(sdkInstanceName) {
        it.networkMonitor?.notifyInterceptorInstantiated()
    }

    /**
     * Sends an event to indicate that resource timing information is expected for this request.
     * @param requestInfo the request information
     */
    fun sendWaitForResourceTimingEvent(requestInfo: HttpRequestInfo) = ifRumEnabled { sdkCore ->
        sdkCore.networkMonitor?.waitForResourceTiming(buildResourceId(requestInfo, generateUuid = true))
    }

    /**
     * Sends resource timing information for a request.
     * @param requestInfo the request information
     * @param resourceTiming the timing information to report
     */
    fun sendTiming(requestInfo: HttpRequestInfo, resourceTiming: ResourceTiming) = ifRumEnabled { sdkCore ->
        sdkCore.networkMonitor?.addResourceTiming(
            buildResourceId(requestInfo, generateUuid = false),
            resourceTiming
        )
    }

    /**
     * Starts tracking a network resource.
     * @param requestInfo the request information
     */
    fun startResource(requestInfo: HttpRequestInfo) = ifRumEnabled { sdkCore ->
        sdkCore.networkMonitor?.startResource(
            buildResourceId(requestInfo, generateUuid = true),
            requestInfo.toRumResourceMethod(networkInstrumentationName, sdkCore.internalLogger),
            requestInfo.url
        )
    }

    /**
     * Stops tracking a network resource with a successful response.
     * @param requestInfo the request information
     * @param responseInfo the response information
     */
    fun stopResource(requestInfo: HttpRequestInfo, responseInfo: HttpResponseInfo) = ifRumEnabled { sdkCore ->
        sdkCore.networkMonitor?.stopResource(
            buildResourceId(requestInfo, generateUuid = false),
            responseInfo.statusCode,
            responseInfo.getBodyLength(),
            responseInfo.getRumResourceKind(),
            rumResourceAttributesProvider.onProvideAttributes(requestInfo, responseInfo, null)
        )
    }

    /**
     * Stops tracking a network resource with an error.
     * @param requestInfo the request information
     * @param throwable the error that occurred
     */
    fun stopResourceWithError(requestInfo: HttpRequestInfo, throwable: Throwable) = ifRumEnabled { sdkCore ->
        sdkCore.networkMonitor?.stopResourceWithError(
            buildResourceId(requestInfo, generateUuid = false),
            null,
            ERROR_MSG_FORMAT.format(Locale.US, networkInstrumentationName, requestInfo.method, requestInfo.url),
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
                { WARN_RUM_DISABLED.format(Locale.US, networkInstrumentationName, prefix) }
            )
        }
    }

    companion object {
        internal const val ERROR_MSG_FORMAT = "%s request error %s %s"
        internal const val UNSUPPORTED_HTTP_METHOD =
            "Unsupported HTTP method %s reported by %s instrumentation, using GET instead"

        internal const val WARN_RUM_DISABLED =
            "You set up a %s instrumentation for %s, but RUM feature is disabled. " +
                "Make sure you initialized the Datadog SDK with a valid Application ID, " +
                "and that RUM feature is enabled."

        /**
         * Builds a ResourceId for the given HTTP request information.
         *
         * @param request the HTTP request information used to construct the resource ID
         * @param generateUuid a flag indicating whether a new UUID should be generated if none is provided
         * @return the generated ResourceId containing a key and optionally a UUID
         */
        fun buildResourceId(request: HttpRequestInfo, generateUuid: Boolean): ResourceId {
            val uuid = request.tag(UUID::class.java) ?: (if (generateUuid) UUID.randomUUID() else null)
            val key = identifyRequest(request)

            return ResourceId(key, uuid?.toString())
        }

        private fun identifyRequest(requestInfo: HttpRequestInfo): String {
            val method = requestInfo.method
            val url = requestInfo.url

            val contentLength = requestInfo.contentLength() ?: 0
            val contentType = requestInfo.contentType
            // TODO RUM-648 It is possible that if requests are say GZIPed (as an example), or maybe
            //  streaming case (?), they all will have contentLength = -1, so if they target the same URL
            //  they are going to have same identifier, messing up reporting.
            //  -1 is valid return value for contentLength() call according to the docs and stands
            //  for "unknown" case.
            //  We need to have a more precise identification.
            return if (contentType != null || contentLength != 0L) {
                "$method•$url•$contentLength•$contentType"
            } else {
                "$method•$url"
            }
        }

        private fun HttpRequestInfo.toRumResourceMethod(
            networkInstrumentationName: String,
            internalLogger: InternalLogger
        ) =
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
                        { UNSUPPORTED_HTTP_METHOD.format(Locale.US, method, networkInstrumentationName) }
                    )
                    RumResourceMethod.GET
                }
            }

        private fun HttpResponseInfo.getRumResourceKind() =
            when (val mimeType = contentType) {
                null -> RumResourceKind.NATIVE
                else -> RumResourceKind.fromMimeType(mimeType)
            }

        private fun HttpResponseInfo.getBodyLength(): Long? {
            val isStream = HttpSpec.ContentType.isStream(contentType)
            val isWebSocket = !headers[HttpSpec.Headers.WEBSOCKET_ACCEPT_HEADER].isNullOrEmpty()
            return if (isStream || isWebSocket) null else contentLength
        }

        private val SdkCore.networkMonitor: AdvancedNetworkRumMonitor?
            get() = (GlobalRumMonitor.get(this) as? AdvancedNetworkRumMonitor)
    }
}
