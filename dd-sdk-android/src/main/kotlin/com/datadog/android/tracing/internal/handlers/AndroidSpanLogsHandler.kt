package com.datadog.android.tracing.internal.handlers

import android.util.Log
import com.datadog.android.log.Logger
import datadog.opentracing.DDSpan
import datadog.opentracing.LogHandler
import datadog.trace.api.DDTags
import io.opentracing.log.Fields.ERROR_OBJECT
import io.opentracing.log.Fields.MESSAGE
import java.util.concurrent.TimeUnit

internal class AndroidSpanLogsHandler(
    val logger: Logger
) : LogHandler {

    // region Span
    override fun log(timestampMicroseconds: Long, fields: Map<String, *>, span: DDSpan) {
        extractError(fields, span)
        logFields(fields, toMilliseconds(timestampMicroseconds))
    }

    override fun log(event: String, span: DDSpan) {
        logger.v(event)
    }

    override fun log(timestampMicroseconds: Long, event: String, span: DDSpan) {
        logger.internalLog(
            Log.VERBOSE,
            event,
            null,
            emptyMap(),
            timestamp = toMilliseconds(timestampMicroseconds)
        )
    }

    override fun log(map: Map<String, *>, span: DDSpan) {
        extractError(map, span)
        logFields(map)
    }

    // endregion

    // region Internal

    private fun toMilliseconds(timestampMicroseconds: Long): Long {
        return TimeUnit.MILLISECONDS.toSeconds(timestampMicroseconds)
    }

    private fun logFields(fields: Map<String, *>, timestamp: Long? = null) {
        if (timestamp != null) {
            logger.internalLog(
                Log.VERBOSE,
                TRACE_LOG_MESSAGE,
                null,
                localAttributes = fields,
                timestamp = timestamp
            )
        } else {
            logger.v(TRACE_LOG_MESSAGE, attributes = fields)
        }
    }

    // endregion

    // region Internal
    private fun extractError(
        map: Map<String, *>,
        span: DDSpan
    ) {
        val throwable = map[ERROR_OBJECT] as? Throwable
        if (throwable != null) {
            span.setErrorMeta(throwable)
        } else {
            (map[MESSAGE] as? String)?.let {
                span.setTag(DDTags.ERROR_MSG, it)
            }
        }
    }

    // endregion
    companion object {
        internal const val TRACE_LOG_MESSAGE = "Span log"
    }
}
