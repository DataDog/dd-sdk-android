/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.cronet.internal

import org.chromium.net.UrlRequest
import java.nio.ByteBuffer
import java.util.UUID

internal class CronetUrlRequest(
    private val initialRequestInfo: CronetHttpRequestInfo,
    private val requestCallback: CronetRequestCallback
) : UrlRequest() {

    @Volatile
    private var delegatedRequest: UrlRequest? = null

    override fun start() {
        // Tag the request with a unique identifier before any instrumentation reads it, so that all
        // the RUM resource events of this request (start/timing/stop) resolve to the very same
        // ResourceId. Without it the ResourceId falls back to a method+url+body based key, and
        // concurrent requests to the same URL end up sharing a single RUM resource scope.
        val requestInfo = initialRequestInfo.withUniqueResourceIdTag()
        val requestTracingState = requestCallback.onRequestStarted(requestInfo)
        val requestInfoBuilder =
            (requestTracingState.requestInfoBuilder as? CronetHttpRequestInfoBuilder)
                ?: requestInfo.newBuilder()

        requestInfoBuilder
            .buildCronetRequest(requestTracingState)
            .also { delegatedRequest = it }
            .start()
    }

    private fun CronetHttpRequestInfo.withUniqueResourceIdTag(): CronetHttpRequestInfo =
        if (tag(UUID::class.java) != null) {
            // Already identified, keep that identifier: this mirrors RumNetworkInstrumentation's own
            // "reuse the tag, else generate" contract. Overwriting it would drop a UUID annotation
            // the application attached itself and reads back from RequestFinishedInfo, and appending
            // to the annotations would leave ours behind the existing one, where it is never read.
            this
        } else {
            newBuilder()
                .addTag(UUID::class.java, UUID.randomUUID())
                .build()
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
