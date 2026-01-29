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
    private val cronetInstrumentationStateHolder: CronetInstrumentationStateHolder
) : UrlRequest() {

    @Volatile
    private var delegatedRequest: UrlRequest? = null

    override fun start() {
        val requestInfo: CronetHttpRequestInfo = requestContext.buildRequestInfo()
        requestContext.rumResourceInstrumentation?.apply {
            startResource(requestInfo)
            sendWaitForResourceTimingEvent(requestInfo)
        }

        val traceState = requestContext.networkTracingInstrumentation?.onRequest(requestInfo)
            ?.also { traceState -> cronetInstrumentationStateHolder.traceState = traceState }

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
