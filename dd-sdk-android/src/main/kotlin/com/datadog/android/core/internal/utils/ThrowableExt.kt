package com.datadog.android.core.internal.utils

import java.io.PrintWriter
import java.io.StringWriter

internal fun Throwable.loggableStackTrace(): String {
    val stringWriter = StringWriter()
    printStackTrace(PrintWriter(stringWriter))
    return stringWriter.toString()
}
