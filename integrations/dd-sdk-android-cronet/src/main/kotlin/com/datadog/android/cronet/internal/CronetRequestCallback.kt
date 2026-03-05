/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.cronet.internal

import com.datadog.android.rum.internal.net.RumNetworkInstrumentation
import com.datadog.android.trace.ApmNetworkTracingScope
import com.datadog.android.trace.internal.ApmNetworkInstrumentation
import com.datadog.android.trace.internal.net.RequestTracingState
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

@Suppress("UnsafeThirdPartyFunctionCall") // Cronet callback delegation is safe
internal class CronetRequestCallback(
    private val delegate: UrlRequest.Callback,
    private val apmNetworkInstrumentation: ApmNetworkInstrumentation?,
    private val rumNetworkInstrumentation: RumNetworkInstrumentation?,
    private val distributedTracingInstrumentation: ApmNetworkInstrumentation?
) : UrlRequest.Callback() {

    private val apmTracingStateHolder = AtomicReference<RequestTracingState?>(null)
    private val distributedTracingStateHolder = AtomicReference<RequestTracingState?>(null)

    internal fun onRequestStarted(initialRequestInfo: CronetHttpRequestInfo): RequestTracingState {
        val distributedTracingState = distributedTracingInstrumentation?.onRequest(initialRequestInfo)
            .also(distributedTracingStateHolder::set)

        val finalRequestInfo = distributedTracingState?.createRequestInfo() ?: initialRequestInfo

        rumNetworkInstrumentation?.apply {
            startResource(finalRequestInfo)
            sendWaitForResourceTimingEvent(finalRequestInfo)
        }

        apmNetworkInstrumentation?.onRequest(finalRequestInfo)
            .also(apmTracingStateHolder::set)

        return distributedTracingState ?: RequestTracingState(initialRequestInfo.newBuilder())
    }

    override fun onRedirectReceived(
        cronetRequest: UrlRequest,
        cronetResponse: UrlResponseInfo?,
        newLocationUrl: String?
    ) {
        if (apmNetworkInstrumentation?.networkTracingScope != ApmNetworkTracingScope.ALL) {
            // Redirects instrumented only with ApmNetworkTracingScope.ALL scope
            delegate.onRedirectReceived(cronetRequest, cronetResponse, newLocationUrl)
            return
        }

        val requestInfo = apmTracingStateHolder.get()?.createRequestInfo()

        finishSuccessRequestTracing(cronetResponse)

        val redirectRequestWrapper = CronetRedirectTracingRequestWrapper(
            cronetRequest,
            requestInfo,
            newLocationUrl,
            cronetResponse?.httpStatusCode,
            apmNetworkInstrumentation,
            apmTracingStateHolder
        )

        delegate.onRedirectReceived(redirectRequestWrapper, cronetResponse, newLocationUrl)
    }

    override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo?) {
        finishSuccessRequestTracing(info)
        delegate.onSucceeded(request, info)
    }

    override fun onCanceled(request: UrlRequest?, info: UrlResponseInfo?) {
        finishFailedRequestTracing(IOException("Response cancelled"))
        delegate.onCanceled(request, info)
    }

    override fun onFailed(
        request: UrlRequest?,
        info: UrlResponseInfo?,
        error: CronetException?
    ) {
        finishFailedRequestTracing(error ?: IOException("Response failed"))
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

    private fun finishSuccessRequestTracing(response: UrlResponseInfo?) {
        val responseInfo = response?.let { CronetHttpResponseInfo(it) }

        apmTracingStateHolder.getAndSet(null)?.let { apmTracingState ->
            if (responseInfo != null) {
                apmNetworkInstrumentation?.onResponseSucceeded(apmTracingState, responseInfo)
            }
        } ?: apmNetworkInstrumentation?.reportInstrumentationError {
            "Request tracing state not found for ${response?.url}. Instrumentation may be broken."
        }

        distributedTracingStateHolder.getAndSet(null)?.let { distributedTracingState ->
            if (responseInfo != null) {
                distributedTracingInstrumentation?.onResponseSucceeded(distributedTracingState, responseInfo)
            }
        }
    }

    private fun finishFailedRequestTracing(exception: Exception) {
        apmTracingStateHolder.getAndSet(null)?.let { state ->
            apmNetworkInstrumentation?.onResponseFailed(
                requestTracingState = state,
                throwable = exception
            )
        }

        distributedTracingStateHolder.getAndSet(null)?.let { state ->
            distributedTracingInstrumentation?.onResponseFailed(
                requestTracingState = state,
                throwable = exception
            )
        }
    }
}
