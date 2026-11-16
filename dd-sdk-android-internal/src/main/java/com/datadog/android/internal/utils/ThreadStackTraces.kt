/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

/**
 * Receives a message and its cause when a stack trace capture fails.
 */
typealias StackTraceErrorHandler = (message: String, throwable: Throwable) -> Unit

/**
 * Captures the stack traces of all live threads, given a [StackTraceErrorHandler].
 */
typealias AllStackTracesProvider = (onError: StackTraceErrorHandler) -> Map<Thread, Array<StackTraceElement>>

/**
 * Captures the stack traces of all live threads.
 *
 * Uses [Thread.getAllStackTraces], which fails entirely if a single thread fails. In that case,
 * falls back to capturing each live thread separately. In the fallback, recoverable runtime
 * failures (a [SecurityException] or any other [RuntimeException] thrown while capturing a
 * single thread) are treated as capture failures: they are reported once via [onError], with a
 * stable message (so callers can safely use `onlyOnce`) together with the first error
 * encountered, and the affected threads are skipped. Fatal [Error]s (such as
 * [OutOfMemoryError]) are never caught and propagate. This fallback is not an atomic snapshot:
 * threads are captured one at a time.
 *
 * @param onError called with a message and the cause of each failure.
 * @return the stack traces per thread; empty if nothing could be captured.
 */
fun safeGetAllStackTraces(onError: StackTraceErrorHandler): Map<Thread, Array<StackTraceElement>> {
    return safeGetAllStackTraces(
        onError = onError,
        bulkStackTracesProvider = {
            @Suppress("UnsafeThirdPartyFunctionCall")
            Thread.getAllStackTraces()
        },
        liveThreadsProvider = ::enumerateLiveThreads,
        threadStackTraceProvider = { it.stackTrace }
    )
}

internal fun safeGetAllStackTraces(
    onError: StackTraceErrorHandler,
    bulkStackTracesProvider: () -> Map<Thread, Array<StackTraceElement>>,
    liveThreadsProvider: () -> List<Thread>,
    threadStackTraceProvider: (Thread) -> Array<StackTraceElement>
): Map<Thread, Array<StackTraceElement>> {
    return try {
        bulkStackTracesProvider()
    } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
        // coroutines machinery can throw errors here
        onError(BULK_CAPTURE_FAILED, t)
        perThreadStackTraces(onError, liveThreadsProvider, threadStackTraceProvider)
    }
}

private fun perThreadStackTraces(
    onError: StackTraceErrorHandler,
    liveThreadsProvider: () -> List<Thread>,
    threadStackTraceProvider: (Thread) -> Array<StackTraceElement>
): Map<Thread, Array<StackTraceElement>> {
    val threads = try {
        liveThreadsProvider()
    } catch (e: SecurityException) {
        onError(ENUMERATE_FAILED, e)
        return emptyMap()
    }
    val result = LinkedHashMap<Thread, Array<StackTraceElement>>()
    var firstFailure: RuntimeException? = null
    for (thread in threads) {
        try {
            result[thread] = threadStackTraceProvider(thread)
        } catch (
            // recoverable per-thread runtime failures are skipped and aggregated; fatal Errors
            // (e.g. OutOfMemoryError) are not caught and must propagate
            @Suppress("TooGenericExceptionCaught")
            e: RuntimeException
        ) {
            if (firstFailure == null) firstFailure = e
        }
    }
    firstFailure?.let { onError(THREAD_CAPTURE_FAILED, it) }
    return result
}

internal fun enumerateLiveThreads(): List<Thread> {
    var root = Thread.currentThread().threadGroup ?: return emptyList()
    while (true) root = root.parent ?: break
    return enumerateLiveThreads(root)
}

@Suppress("UnsafeThirdPartyFunctionCall") // SecurityException is handled by perThreadStackTraces
internal fun enumerateLiveThreads(root: ThreadGroup): List<Thread> {
    var buffer = arrayOfNulls<Thread>(root.activeCount() + THREAD_COUNT_MARGIN)
    var count = root.enumerate(buffer, true)
    var attempts = 1
    // ponytail: after MAX_ENUMERATE_ATTEMPTS the list may be truncated; fine for a best-effort dump
    while (count == buffer.size && attempts < MAX_ENUMERATE_ATTEMPTS) {
        buffer = arrayOfNulls(buffer.size * 2)
        count = root.enumerate(buffer, true)
        attempts++
    }
    return buffer.take(count).filterNotNull()
}

private const val THREAD_COUNT_MARGIN = 16
private const val MAX_ENUMERATE_ATTEMPTS = 3
internal const val BULK_CAPTURE_FAILED = "Failed to get all stack traces, falling back to per-thread capture."
internal const val ENUMERATE_FAILED = "Failed to enumerate live threads."
internal const val THREAD_CAPTURE_FAILED =
    "Failed to get the stack trace of some thread(s), skipping them."
