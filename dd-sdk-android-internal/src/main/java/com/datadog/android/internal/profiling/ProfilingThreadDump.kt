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
 * @param state JVM thread state captured at dump time.
 * @param stack Serialized stack trace (lines joined with newline).
 */
data class ProfilingThreadDump(
    val name: String,
    val state: Thread.State,
    val stack: String
)
