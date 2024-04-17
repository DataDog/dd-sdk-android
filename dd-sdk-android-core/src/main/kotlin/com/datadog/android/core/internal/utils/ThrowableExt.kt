/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import com.datadog.android.lint.InternalApi
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Converts stacktrace to string format.
 */
@InternalApi
fun Throwable.loggableStackTrace(): String {
    val stringWriter = StringWriter()
    @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
    printStackTrace(PrintWriter(stringWriter))
    return stringWriter.toString()
}
