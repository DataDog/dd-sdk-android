/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.profiling

/**
 * Thread snapshot used within [ProfilingAnrDetectedEvent].
 *
 * Defined here (in `dd-sdk-android-internal`) so that [ProfilingAnrDetectedEvent] can live
 * in this module without creating a circular dependency on `dd-sdk-android-core`.
 *
 * @param name Thread name.
 * @param state Thread state string (e.g. `Thread.State.name.lowercase()`).
 * @param stack Serialized stack trace (lines joined with newline).
 * @param crashed Whether this thread was the one that triggered the ANR.
 */
data class ProfilingThreadDump(
    val name: String,
    val state: String,
    val stack: String,
    val crashed: Boolean
)
