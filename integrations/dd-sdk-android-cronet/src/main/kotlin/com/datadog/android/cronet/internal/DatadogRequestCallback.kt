/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.cronet.internal

import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.MutableHttpRequestInfo
import com.datadog.android.trace.ApmNetworkTracingScope
import com.datadog.android.trace.internal.ApmNetworkInstrumentation
import com.datadog.android.trace.internal.net.RequestTraceState
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

@Suppress("UnsafeThirdPartyFunctionCall") // Cronet callback delegation is safe
internal class DatadogRequestCallback(
    private val delegate: UrlRequest.Callback,
    private val apmNetworkInstrumentation: ApmNetworkInstrumentation?
) : UrlRequest.Callback() {

    private val requestTracingSnapshot = AtomicReference(RequestTracingSnapshot())

    internal fun onRequestStarted(requestInfo: HttpRequestInfo): RequestTraceState? {
        val traceState = apmNetworkInstrumentation?.onRequest(requestInfo)
        requestTracingSnapshot.set(RequestTracingSnapshot(traceState = traceState, requestInfo = requestInfo))
        return traceState
    }

    override fun onRedirectReceived(
        request: UrlRequest,
        info: UrlResponseInfo?,
        newLocationUrl: String?
    ) {
        if (apmNetworkInstrumentation?.networkTracingScope != ApmNetworkTracingScope.DETAILED) {
            delegate.onRedirectReceived(request, info, newLocationUrl)
            return
        }

        finishTracingForSuccessfulRequest(info)

        val redirectRequest = RedirectTrackingUrlRequest(request).also {
            delegate.onRedirectReceived(it, info, newLocationUrl)
        }

        if (redirectRequest.wasFollowRedirectCalled) {
            requestTracingSnapshot.get().requestInfo?.instrumentRedirect(newLocationUrl)
        } else {
            apmNetworkInstrumentation.reportInstrumentationError(FOLLOW_REDIRECT_IS_NOT_DETECTED_MESSAGE)
        }
    }

    override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo?) {
        finishTracingForSuccessfulRequest(info)
        delegate.onSucceeded(request, info)
    }

    override fun onCanceled(request: UrlRequest?, info: UrlResponseInfo?) {
        finishTracingForFailedRequest(IOException("Response cancelled"))
        delegate.onCanceled(request, info)
    }

    override fun onFailed(
        request: UrlRequest?,
        info: UrlResponseInfo?,
        error: CronetException?
    ) {
        finishTracingForFailedRequest(error ?: IOException("Response failed"))
        delegate.onFailed(request, info, error)
    }

    override fun onResponseStarted(
        request: UrlRequest?,
        info: UrlResponseInfo?
    ) = delegate.onResponseStarted(request, info)

    override fun onReadCompleted(
        request: UrlRequest?,
        info: UrlResponseInfo?,
        byteBuffer: ByteBuffer?
    ) = delegate.onReadCompleted(request, info, byteBuffer)

    private fun HttpRequestInfo.instrumentRedirect(redirectUrl: String?) {
        if (this !is MutableHttpRequestInfo || redirectUrl == null || apmNetworkInstrumentation == null) return

        val redirectRequestInfo = newBuilder()
            .setUrl(redirectUrl)
            // Clear existing tracing headers so that each redirect creates a new independent span/trace
            // This matches OkHttp behavior where each redirect is a separate root span
            .also(apmNetworkInstrumentation::removeTracingHeaders)
            .build()

        val newTraceState = apmNetworkInstrumentation.onRequest(redirectRequestInfo)
        requestTracingSnapshot.set(
            RequestTracingSnapshot(traceState = newTraceState, requestInfo = redirectRequestInfo)
        )
    }

    private fun finishTracingForSuccessfulRequest(info: UrlResponseInfo?) {
        val snapshot = requestTracingSnapshot.get()
        snapshot.traceState?.let { traceState ->
            if (info != null) {
                apmNetworkInstrumentation?.onResponseSucceeded(traceState, CronetHttpResponseInfo(info))
            }
            requestTracingSnapshot.set(snapshot.copy(traceState = null))
        }
    }

    private fun finishTracingForFailedRequest(exception: Exception) {
        val snapshot = requestTracingSnapshot.get()
        snapshot.traceState?.let { traceState ->
            apmNetworkInstrumentation?.onResponseFailed(traceState, exception)
            requestTracingSnapshot.set(snapshot.copy(traceState = null))
        }
    }

    private data class RequestTracingSnapshot(
        val traceState: RequestTraceState? = null,
        val requestInfo: HttpRequestInfo? = null
    )

    internal companion object {
        const val FOLLOW_REDIRECT_IS_NOT_DETECTED_MESSAGE =
            "Redirect not followed - APM trace chain may be broken"
    }
}
