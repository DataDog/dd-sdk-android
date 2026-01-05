/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.okhttp.internal

import com.datadog.android.trace.internal.net.RequestTracingState
import okhttp3.Call
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiFunction

internal class RequestTracingStateRegistry {
    private val requestTracingStateByCall = ConcurrentHashMap<Call, RequestTracingState>()

    fun register(call: Call) {
        val taggedRequest = OkHttpRequestInfoBuilder(call.request().newBuilder())
            .addTag(UUID::class.java, UUID.randomUUID())

        requestTracingStateByCall[call] = RequestTracingState(taggedRequest)
    }

    fun update(
        call: Call,
        block: BiFunction<Call, RequestTracingState, RequestTracingState?>
    ): RequestTracingState? = requestTracingStateByCall.computeIfPresent(call, block)

    fun get(call: Call): RequestTracingState? = requestTracingStateByCall[call]

    fun remove(call: Call): RequestTracingState? = requestTracingStateByCall.remove(call)
}
