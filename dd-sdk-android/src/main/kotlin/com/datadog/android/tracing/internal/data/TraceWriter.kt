/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal.data

import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import datadog.opentracing.DDSpan
import datadog.trace.api.DDTags
import datadog.trace.common.writer.Writer
import java.util.Locale

internal class TraceWriter(
    val writer: com.datadog.android.core.internal.data.Writer<DDSpan>
) : Writer {

    // region Writer
    override fun start() {
        // NO - OP
    }

    override fun write(trace: MutableList<DDSpan>?) {
        trace?.let {
            it.filter { it.isError }.forEach { span ->
                sendRumErrorEvent(span)
            }
            writer.write(it)
        }
    }

    override fun close() {
        // NO - OP
    }

    override fun incrementTraceCount() {
        // NO - OP
    }

    // endregion

    // region Internals

    private fun sendRumErrorEvent(span: DDSpan) {
        (GlobalRum.get() as? AdvancedRumMonitor)?.addErrorWithStacktrace(
            spanErrorMessage(span),
            RumErrorSource.SOURCE,
            span.tags[DDTags.ERROR_STACK]?.toString(),
            emptyMap()
        )
    }

    private fun spanErrorMessage(span: DDSpan): String {
        val errorType = span.tags[DDTags.ERROR_TYPE]
        val errorMessage = span.tags[DDTags.ERROR_MSG]
        return when {
            errorMessage != null && errorType != null ->
                SPAN_ERROR_WITH_TYPE_AND_MESSAGE_FORMAT.format(
                    Locale.US,
                    span.operationName,
                    errorType,
                    errorMessage
                )
            errorType != null ->
                SPAN_ERROR_WITH_TYPE_FORMAT.format(
                    Locale.US,
                    span.operationName,
                    errorType
                )
            errorMessage != null ->
                SPAN_ERROR_WITH_MESSAGE_FORMAT.format(
                    Locale.US,
                    span.operationName,
                    errorMessage
                )
            else -> SPAN_ERROR_FORMAT.format(Locale.US, span.operationName)
        }
    }

    // endregion

    companion object {
        internal const val SPAN_ERROR_FORMAT: String = "Span error (%s)"
        internal const val SPAN_ERROR_WITH_TYPE_AND_MESSAGE_FORMAT: String =
            "Span error (%s): %s | %s "
        internal const val SPAN_ERROR_WITH_TYPE_FORMAT: String = "Span error (%s): %s"
        internal const val SPAN_ERROR_WITH_MESSAGE_FORMAT: String = "Span error (%s): %s"
    }
}
