/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

import java.io.PrintWriter
import java.io.StringWriter

/**
 * Converts stacktrace to string format.
 */
fun Throwable.loggableStackTrace(): String {
    val stringWriter = StringWriter()
    return try {
        @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
        printStackTrace(PrintWriter(stringWriter))
        stringWriter.toString()
    } catch (@Suppress("TooGenericExceptionCaught") _: Throwable) {
        // printStackTrace may throw when formatting cause/suppressed exceptions (e.g. a
        // kotlinx.coroutines bug where toString() on the coroutine context throws).
        // Return whatever was written before the throw (likely the full main frames),
        // or fall back to the raw stack array if nothing was written yet.
        stringWriter.toString().ifBlank { stackTrace.loggableStackTrace() }
    }
}
