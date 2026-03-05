/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.cronet.internal

import org.chromium.net.UrlRequest
import java.nio.ByteBuffer

internal class CronetUrlRequest(
    private val initialRequestInfo: CronetHttpRequestInfo,
    private val requestCallback: CronetRequestCallback
) : UrlRequest() {

    @Volatile
    private var delegatedRequest: UrlRequest? = null

    override fun start() {
        val requestTracingState = requestCallback.onRequestStarted(initialRequestInfo)
        val requestInfoBuilder =
            (requestTracingState.requestInfoBuilder as? CronetHttpRequestInfoBuilder)
                ?: initialRequestInfo.newBuilder()

        requestInfoBuilder
            .buildCronetRequest(requestTracingState)
            .also { delegatedRequest = it }
            .start()
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
