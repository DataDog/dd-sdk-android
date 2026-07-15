/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.profiling

/**
 * Sent by the profiling feature to the RUM feature when ProfilingManager
 * notifies us of an ANR via the trigger-based capture API.
 *
 * @param detectedAtMs Timestamp (device clock, millis since epoch) when the
 *   profiling feature handled the ANR trigger callback.
 * @param anrThreadStack Stack trace of the thread that triggered the ANR
 *   (in practice the main looper thread) at capture time. Used as the
 *   synthesized `ANRException` stack trace on the RUM side.
 * @param anrThreadName Name of the thread that triggered the ANR.
 * @param anrThreadState JVM thread state of the ANR thread at capture time.
 * @param allThreads Per-thread dump for every other live thread (ANR thread excluded).
 */
data class ProfilingAnrDetectedEvent(
    val detectedAtMs: Long,
    val anrThreadStack: List<StackTraceElement>,
    val anrThreadName: String,
    val anrThreadState: Thread.State,
    val allThreads: List<ProfilingThreadDump>
)
