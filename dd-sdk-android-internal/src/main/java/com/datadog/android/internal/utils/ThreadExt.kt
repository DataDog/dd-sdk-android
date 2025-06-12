/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

import android.os.Build

/**
 * getId() method got deprecated on Android 36.
 * https://android-review.googlesource.com/c/platform/libcore/+/3380110/3/ojluni/src/main/java/java/lang/Thread.java#b2114
 * But threadId() is part of hidden API before Android 36, so we use getId() on those older versions.
 */
fun Thread.safeGetThreadId(): Long {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        threadId()
    } else {
        @Suppress("DEPRECATION")
        id
    }
}

/**
 * Converts Thread state to string format. This is needed, because enum may be obfuscated, so we
 * cannot rely on the name property.
 */
fun Thread.State.asString(): String {
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
fun Array<StackTraceElement>.loggableStackTrace(): String = joinToString("\n") { "at $it" }
