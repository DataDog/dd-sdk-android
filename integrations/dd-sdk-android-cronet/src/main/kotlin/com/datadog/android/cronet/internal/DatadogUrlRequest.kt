/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.cronet.internal

import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import org.chromium.net.UrlRequest
import java.nio.ByteBuffer

internal class DatadogUrlRequest(
    private val requestContext: DatadogCronetRequestContext,
    private val requestCallback: DatadogRequestCallback
) : UrlRequest() {

    @Volatile
    private var delegatedRequest: UrlRequest? = null

    override fun start() {
        val requestInfo: CronetHttpRequestInfo = requestContext.buildRequestInfo()
        requestContext.rumNetworkInstrumentation?.apply {
            startResource(requestInfo)
            sendWaitForResourceTimingEvent(requestInfo)
        }

        val traceState = requestCallback.onRequestStarted(requestInfo)
        val finalRequestInfo: HttpRequestInfo = traceState?.requestInfo ?: requestInfo

        (finalRequestInfo as? CronetHttpRequestInfo)
            ?.buildCronetRequest(traceState)
            ?.also { delegatedRequest = it }
            ?.start()
    }

    override fun cancel() {
        delegatedRequest?.cancel()
    }

    override fun followRedirect() {
        delegatedRequest?.followRedirect()
    }

    override fun read(buffer: ByteBuffer?) {
        delegatedRequest?.read(buffer)
    }

    override fun getStatus(listener: StatusListener?) {
        delegatedRequest?.getStatus(listener)
    }

    override fun isDone(): Boolean = delegatedRequest?.isDone ?: false
}
