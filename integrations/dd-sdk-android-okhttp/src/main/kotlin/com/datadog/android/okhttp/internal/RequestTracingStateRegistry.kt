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
import java.util.concurrent.atomic.AtomicInteger

/**
 * Striped lock pool for per-call synchronization.
 *
 * Using the call object itself as a monitor (`synchronized(call)`) is an anti-pattern:
 * external code could synchronize on the same object, risking deadlocks.
 * Instead, we use a fixed pool of private lock objects selected by hashCode.
 *
 * Benchmark results (1000 calls, 5 rounds, JVM unit tests):
 *
 * | Strategy              | 5 threads  | 10 threads | 100 threads |
 * |-----------------------|------------|------------|-------------|
 * | synchronized(call)    | baseline   | baseline   | baseline    |
 * | Striped 32            | +11.6%     | +3.4%      | +6.7%       |
 * | Striped 64 (chosen)   | -3.8% ✓    | +0.3%      | +3.6%       |
 * | Global lock + HashMap | +35.5%     | +37.0%     | -2.2%       |
 *
 * 64 stripes chosen: best performance at low thread counts (biased locking benefits),
 * negligible fixed memory overhead (~1.3 KB), API 23 compatible.
 */
internal class RequestTracingStateRegistry(
    private val internalLogger: InternalLogger
) {
    private val requestTracingStateByCall = ConcurrentHashMap<Call, RequestTracingState>()

    private val callCount = AtomicInteger(0)

    private val stripes = Array(MAX_LOCKS_BUCKETS_SIZE) { Any() }

    private fun lockFor(call: Call) = stripes[call.hashCode().and(POSITIVE_INT_MASK) % stripes.size]

    fun register(call: Call) = synchronized(lockFor(call)) {
        if (callCount.incrementAndGet() > MAX_TRACKED_CALLS) {
            callCount.decrementAndGet()
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

    fun get(call: Call): RequestTracingState? = synchronized(lockFor(call)) {
        requestTracingStateByCall[call]
    }

    fun remove(call: Call): RequestTracingState? = synchronized(lockFor(call)) {
        requestTracingStateByCall.remove(call)?.also { callCount.decrementAndGet() }
    }

    fun restoreUUIDTag(call: Call, request: Request): Request? = update(call) { current ->
        current.copy(
            requestInfoBuilder = request.toHttpRequestInfo()
                .newBuilder()
                .restoreUUIDTag(current.createRequestInfo())
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
    ): RequestTracingState? = synchronized(lockFor(call)) {
        requestTracingStateByCall[call]
            ?.let(mapper)
            ?.also { requestTracingStateByCall[call] = it }
    }

    internal companion object {
        internal const val MAX_TRACKED_CALLS = 256
        private const val MAX_LOCKS_BUCKETS_SIZE = 64
        private const val POSITIVE_INT_MASK = 0x7fff_ffff
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
