/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.okhttp.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.HttpRequestInfoBuilder
import com.datadog.android.api.instrumentation.network.tag
import com.datadog.android.trace.internal.net.RequestTracingState
import okhttp3.Call
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal class RequestTracingStateRegistry(
    private val internalLogger: InternalLogger
) {
    private val requestTracingStateByCall = ConcurrentHashMap<Call, RequestTracingState>()

    fun register(call: Call) = synchronized(call) {
        if (requestTracingStateByCall.size >= MAX_TRACKED_CALLS) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { WARNING_MAX_TRACKED_CALLS },
                onlyOnce = true
            )
            return
        }

        requestTracingStateByCall[call] = RequestTracingState(
            call.request()
                .toHttpRequestInfo()
                .newBuilder()
                .addTag(UUID::class.java, UUID.randomUUID())
        )
    }

    fun get(call: Call): RequestTracingState? = synchronized(call) {
        requestTracingStateByCall[call]
    }

    fun remove(call: Call): RequestTracingState? = synchronized(call) {
        requestTracingStateByCall.remove(call)
    }

    fun restoreUUIDTag(call: Call, request: Request): Request? = update(call) { current ->
        current.copy(
            requestInfoBuilder = request.toHttpRequestInfo().newBuilder().restoreUUIDTag(current.createRequestInfo())
        )
    }
        ?.createRequestInfo()
        ?.toOkHttpRequest()

    fun setTracingState(
        call: Call,
        newState: RequestTracingState
    ) = update(call) { current ->
        newState.copy(
            requestInfoBuilder = current.requestInfoBuilder.restoreUUIDTag(current.createRequestInfo())
        )
    }

    private fun update(
        call: Call,
        mapper: (RequestTracingState) -> RequestTracingState
    ): RequestTracingState? = synchronized(call) {
        get(call)
            ?.let(mapper)
            ?.also { requestTracingStateByCall[call] = it }
    }

    internal companion object {
        internal const val MAX_TRACKED_CALLS = 256
        internal const val WARNING_MAX_TRACKED_CALLS =
            "RequestTracingStateRegistry: max tracked calls ($MAX_TRACKED_CALLS) reached. " +
                "New calls will not be tracked. This may indicate a leak — " +
                "ensure callEnd/callFailed events are firing."

        private fun HttpRequestInfoBuilder.restoreUUIDTag(other: HttpRequestInfo) = addTag(
            UUID::class.java,
            other.tag(UUID::class.java)
        )
    }
}
