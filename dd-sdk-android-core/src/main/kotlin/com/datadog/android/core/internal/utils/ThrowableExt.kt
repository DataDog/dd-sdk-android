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
