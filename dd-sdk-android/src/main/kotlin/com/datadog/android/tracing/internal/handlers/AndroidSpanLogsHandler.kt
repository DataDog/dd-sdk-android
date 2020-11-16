/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal.handlers

import android.util.Log
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.Logger
import com.datadog.opentracing.DDSpan
import com.datadog.opentracing.LogHandler
import com.datadog.trace.api.DDTags
import io.opentracing.log.Fields
import java.util.concurrent.TimeUnit

internal class AndroidSpanLogsHandler(
    val logger: Logger
) : LogHandler {

    // region Span

    override fun log(event: String, span: DDSpan) {
        logFields(
            span,
            mutableMapOf(Fields.EVENT to event),
            null
        )
    }

    override fun log(timestampMicroseconds: Long, event: String, span: DDSpan) {
        logFields(
            span,
            mutableMapOf(Fields.EVENT to event),
            timestampMicroseconds
        )
    }

    override fun log(fields: Map<String, *>, span: DDSpan) {
        val mutableMap = fields.toMutableMap()
        extractError(mutableMap, span)
        logFields(span, mutableMap)
    }

    override fun log(timestampMicroseconds: Long, fields: Map<String, *>, span: DDSpan) {
        val mutableMap = fields.toMutableMap()
        extractError(mutableMap, span)
        logFields(span, mutableMap, timestampMicroseconds)
    }

    // endregion

    // region Internal

    private fun toMilliseconds(timestampMicroseconds: Long?): Long? {
        return timestampMicroseconds?.let { TimeUnit.MILLISECONDS.toSeconds(it) }
    }

    private fun logFields(
        span: DDSpan,
        fields: MutableMap<String, Any?>,
        timestampMicroseconds: Long? = null
    ) {
        val message = fields.remove(Fields.MESSAGE)?.toString() ?: DEFAULT_EVENT_MESSAGE
        fields[LogAttributes.DD_TRACE_ID] = span.traceId.toString()
        fields[LogAttributes.DD_SPAN_ID] = span.spanId.toString()
        logger.internalLog(
            Log.VERBOSE,
            message,
            null,
            fields,
            toMilliseconds(timestampMicroseconds)
        )
    }

    private fun extractError(
        map: MutableMap<String, *>,
        span: DDSpan
    ) {
        val throwable = map.remove(Fields.ERROR_OBJECT) as? Throwable
        val kind = map.remove(Fields.ERROR_KIND)
        val errorType = kind?.toString() ?: throwable?.javaClass?.name

        if (errorType != null) {
            val stackField = map.remove(Fields.STACK)
            val msgField = map.remove(Fields.MESSAGE)
            val stack = stackField?.toString() ?: throwable?.loggableStackTrace()
            val message = msgField?.toString() ?: throwable?.message

            span.setError(true)
            span.setTag(DDTags.ERROR_TYPE, errorType)
            span.setTag(DDTags.ERROR_MSG, message)
            span.setTag(DDTags.ERROR_STACK, stack)
        }
    }

    // endregion

    companion object {
        internal const val DEFAULT_EVENT_MESSAGE = "Span event"
    }
}
