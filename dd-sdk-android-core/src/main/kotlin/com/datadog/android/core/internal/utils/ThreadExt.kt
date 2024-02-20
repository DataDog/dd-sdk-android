/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

/**
 * Converts Thread state to string format. This is needed, because enum may be obfuscated, so we
 * cannot rely on the name property.
 */
internal fun Thread.State.asString(): String {
    return when (this) {
        Thread.State.NEW -> "new"
        Thread.State.BLOCKED -> "blocked"
        Thread.State.RUNNABLE -> "runnable"
        Thread.State.TERMINATED -> "terminated"
        Thread.State.TIMED_WAITING -> "timed_waiting"
        Thread.State.WAITING -> "waiting"
    }
}

/**
 * Converts stacktrace to string format.
 */
internal fun Array<StackTraceElement>.loggableStackTrace(): String = joinToString("\n") { "at $it" }
