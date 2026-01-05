/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.cronet.internal

import com.datadog.android.trace.NetworkTracingInstrumentation
import com.datadog.android.trace.internal.net.RequestTraceState
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.io.IOException
import java.nio.ByteBuffer

@Suppress("UnsafeThirdPartyFunctionCall") // Cronet callback delegation is safe
internal class DatadogRequestCallback(
    private val delegate: UrlRequest.Callback,
    private val networkTracingInstrumentation: NetworkTracingInstrumentation?
) : UrlRequest.Callback(), CronetInstrumentationStateHolder {

    @Volatile
    override var traceState: RequestTraceState? = null
    override fun onRedirectReceived(
        request: UrlRequest?,
        info: UrlResponseInfo?,
        newLocationUrl: String?
    ) = delegate.onRedirectReceived(request, info, newLocationUrl)

    override fun onResponseStarted(
        request: UrlRequest?,
        info: UrlResponseInfo?
    ) = delegate.onResponseStarted(request, info)

    override fun onReadCompleted(
        request: UrlRequest?,
        info: UrlResponseInfo?,
        byteBuffer: ByteBuffer?
    ) = delegate.onReadCompleted(request, info, byteBuffer)

    override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
        traceState?.let {
            networkTracingInstrumentation?.onResponseSucceed(
                it,
                CronetHttpResponseInfo(info)
            )
        }
        delegate.onSucceeded(request, info)
    }

    override fun onCanceled(request: UrlRequest?, info: UrlResponseInfo?) {
        traceState?.let {
            networkTracingInstrumentation?.onResponseFailed(
                it,
                IOException("Response cancelled")
            )
        }
        delegate.onCanceled(request, info)
    }

    override fun onFailed(
        request: UrlRequest?,
        info: UrlResponseInfo?,
        error: CronetException?
    ) {
        traceState?.let {
            networkTracingInstrumentation?.onResponseFailed(
                it,
                error ?: IOException("Response failed")
            )
        }
        delegate.onFailed(request, info, error)
    }
}
