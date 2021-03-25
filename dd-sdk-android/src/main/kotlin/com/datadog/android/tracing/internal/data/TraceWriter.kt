/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal.data

import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.opentracing.DDSpan
import com.datadog.trace.api.DDTags
import com.datadog.trace.common.writer.Writer
import java.util.Locale

internal class TraceWriter(
    val writer: DataWriter<DDSpan>
) : Writer {

    // region Writer
    override fun start() {
        // NO - OP
    }

    override fun write(trace: MutableList<DDSpan>?) {
        trace?.let {
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
        val errorType = span.tags[DDTags.ERROR_TYPE] as? String
        val errorMessage = span.tags[DDTags.ERROR_MSG] as? String
        val composedErrorMessage = spanErrorMessage(span.operationName, errorType, errorMessage)
        val attributes = if (errorType != null) {
            mapOf(RumAttributes.INTERNAL_ERROR_TYPE to errorType)
        } else {
            emptyMap()
        }
        (GlobalRum.get() as? AdvancedRumMonitor)?.addErrorWithStacktrace(
            composedErrorMessage,
            RumErrorSource.SOURCE,
            span.tags[DDTags.ERROR_STACK]?.toString(),
            attributes
        )
    }

    private fun spanErrorMessage(
        operationName: String,
        errorType: String?,
        errorMessage: String?
    ): String {
        return when {
            errorMessage != null && errorType != null ->
                SPAN_ERROR_WITH_TYPE_AND_MESSAGE_FORMAT.format(
                    Locale.US,
                    operationName,
                    errorType,
                    errorMessage
                )
            errorType != null ->
                SPAN_ERROR_WITH_TYPE_FORMAT.format(
                    Locale.US,
                    operationName,
                    errorType
                )
            errorMessage != null ->
                SPAN_ERROR_WITH_MESSAGE_FORMAT.format(
                    Locale.US,
                    operationName,
                    errorMessage
                )
            else -> SPAN_ERROR_FORMAT.format(Locale.US, operationName)
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
