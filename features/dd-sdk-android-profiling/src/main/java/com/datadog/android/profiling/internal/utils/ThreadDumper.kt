/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.utils

import android.os.Looper
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.profiling.ProfilingAnrDetectedEvent
import com.datadog.android.internal.profiling.ProfilingThreadDump
import com.datadog.android.internal.utils.AllStackTracesProvider
import com.datadog.android.internal.utils.loggableStackTrace
import com.datadog.android.internal.utils.safeGetAllStackTraces

internal class ThreadDumper(
    private val mainThreadProvider: () -> Thread = { Looper.getMainLooper().thread },
    private val allStackTracesProvider: AllStackTracesProvider = ::safeGetAllStackTraces
) {

    @Volatile
    internal var internalLogger: InternalLogger? = null

    fun dump(detectedAtMs: Long): ProfilingAnrDetectedEvent {
        val mainThread = mainThreadProvider()
        val anrThreadStack = mainThread.stackTrace.toList()
        val allThreads = allStackTracesProvider(::logError)
            .filterKeys { it != mainThread }
            .filterValues { it.isNotEmpty() }
            .map { (thread, stack) ->
                ProfilingThreadDump(
                    name = thread.name,
                    state = thread.state,
                    stack = stack.loggableStackTrace()
                )
            }
        return ProfilingAnrDetectedEvent(
            detectedAtMs = detectedAtMs,
            anrThreadStack = anrThreadStack,
            anrThreadName = mainThread.name,
            anrThreadState = mainThread.state,
            allThreads = allThreads
        )
    }

    private fun logError(message: String, throwable: Throwable) {
        internalLogger?.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { message },
            throwable,
            onlyOnce = true
        )
    }
}
