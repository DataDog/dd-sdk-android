/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.anr

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.feature.event.ThreadDump
import com.datadog.android.internal.utils.appendIfNotEmpty
import java.io.IOException
import java.io.InputStream
import java.util.Locale

/**
 * Thread dump: https://cs.android.com/android/platform/superproject/main/+/main:art/runtime/thread_list.cc;l=255;drc=d00d24530a29b684bec9a895c1da491a6390395f
 */
internal class AndroidTraceParser(
    private val internalLogger: InternalLogger
) {

    internal fun parse(traceInputStream: InputStream): List<ThreadDump> {
        @Suppress("UnsafeThirdPartyFunctionCall") // not 3rd party
        val trace = traceInputStream.safeReadText()

        if (trace.isBlank()) return emptyList()

        return parse(trace)
    }

    @Suppress("CyclomaticComplexMethod")
    private fun parse(trace: String): List<ThreadDump> {
        val threadDumps = mutableListOf<ThreadDump>()

        var isInThreadStackBlock = false
        val currentThreadStack = StringBuilder()
        var currentThreadName: String? = null
        var currentThreadState: String? = null

        @Suppress("LoopWithTooManyJumpStatements")
        for (line in trace.lines()) {
            // we are leaving thread information block
            if (line.isBlank() && isInThreadStackBlock) {
                if (currentThreadStack.isNotEmpty() && currentThreadName != null) {
                    threadDumps += ThreadDump(
                        name = currentThreadName,
                        state = convertThreadState(currentThreadState.orEmpty()),
                        stack = currentThreadStack.toString(),
                        crashed = currentThreadName == "main"
                    )
                }
                currentThreadStack.clear()
                isInThreadStackBlock = false
                continue
            }
            // we are entering thread information block
            if (line.contains(" prio=") && line.contains(" tid=")) {
                isInThreadStackBlock = true
                val threadState = line.split(" ")
                    .lastOrNull()
                val threadName = THREAD_NAME_REGEX.matchEntire(line)
                    ?.groupValues
                    ?.elementAtOrNull(1)
                currentThreadName = threadName
                currentThreadState = threadState
                continue
            }
            if (isInThreadStackBlock && line.trimStart()
                    .let { it.startsWith("at ") || it.startsWith("native: ") }
            ) {
                // it can be also lines in the stack like:
                // - waiting on <0x0dd89f49> (a okhttp3.internal.concurrent.TaskRunner)
                // - locked <0x0dd89f49> (a okhttp3.internal.concurrent.TaskRunner)
                // we want to skip them for now
                // also we want to skip any non-stack lines in the thread info block
                currentThreadStack.appendIfNotEmpty('\n').append(line)
            }
        }

        if (threadDumps.isEmpty()) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { PARSING_FAILURE_MESSAGE }
            )
        }

        return threadDumps
    }

    private fun convertThreadState(threadState: String): String {
        // https://cs.android.com/android/platform/superproject/main/+/main:art/runtime/thread_state.h;l=30;drc=37cec725ba134174eadf63b0eb06b964ffc202fd
        // some values are similar to Java's Thread.State, some are not. For the similar ones we
        // need to have a conversion in order to reduce cardinality
        val convertedState = when (threadState) {
            "TimedWaiting" -> "Timed_Waiting"
            else -> threadState
        }
        return convertedState.lowercase(Locale.US)
    }

    private fun InputStream.safeReadText(): String {
        return try {
            use {
                it.reader().readText()
            }
        } catch (e: IOException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { TRACE_STREAM_READ_FAILURE },
                e
            )
            ""
        }
    }

    companion object {
        const val TRACE_STREAM_READ_FAILURE = "Failed to read crash trace stream."
        const val PARSING_FAILURE_MESSAGE =
            "Parsing tracing information for the exit reason wasn't successful, no thread dumps were parsed."
        val THREAD_NAME_REGEX = Regex("^\"(.+)\".+\$")
    }
}
