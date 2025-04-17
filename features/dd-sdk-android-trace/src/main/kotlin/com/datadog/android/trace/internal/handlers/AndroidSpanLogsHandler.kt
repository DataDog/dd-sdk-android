/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.handlers

import android.util.Log
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.internal.utils.loggableStackTrace
import com.datadog.android.log.LogAttributes
import com.datadog.android.trace.AndroidTracer
import com.datadog.android.trace.internal.utils.traceIdAsHexString
import com.datadog.legacy.trace.api.DDTags
import com.datadog.opentracing.DDSpan
import com.datadog.opentracing.LogHandler
import io.opentracing.log.Fields
import java.util.concurrent.TimeUnit

internal class AndroidSpanLogsHandler(
    private val sdkCore: FeatureSdkCore
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
        return timestampMicroseconds?.let { TimeUnit.MICROSECONDS.toMillis(it) }
    }

    private fun logFields(
        span: DDSpan,
        fields: MutableMap<String, Any?>,
        timestampMicroseconds: Long? = null
    ) {
        val logsFeature = sdkCore.getFeature(Feature.LOGS_FEATURE_NAME)
        val spanLogStatus = fields.remove(AndroidTracer.LOG_STATUS) as? Int

        if (logsFeature != null && fields.isNotEmpty()) {
            val message = fields.remove(Fields.MESSAGE)?.toString() ?: DEFAULT_EVENT_MESSAGE
            fields[LogAttributes.DD_TRACE_ID] = span.context().traceIdAsHexString()
            fields[LogAttributes.DD_SPAN_ID] = span.context().toSpanId()
            val timestamp = toMilliseconds(timestampMicroseconds) ?: System.currentTimeMillis()
            logsFeature.sendEvent(
                mapOf(
                    "type" to "span_log",
                    "loggerName" to TRACE_LOGGER_NAME,
                    "message" to message,
                    "attributes" to fields,
                    "timestamp" to timestamp,
                    "logStatus" to (spanLogStatus ?: Log.VERBOSE)
                )
            )
        } else if (logsFeature == null) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { MISSING_LOG_FEATURE_INFO }
            )
        }
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
            val msgField = map[Fields.MESSAGE]
            val stack = stackField?.toString() ?: throwable?.loggableStackTrace()
            val message = msgField?.toString() ?: throwable?.message

            span.isError = true
            span.setTag(DDTags.ERROR_TYPE, errorType)
            span.setTag(DDTags.ERROR_MSG, message)
            span.setTag(DDTags.ERROR_STACK, stack)
        }
    }

    // endregion

    companion object {
        internal const val DEFAULT_EVENT_MESSAGE = "Span event"

        internal const val MISSING_LOG_FEATURE_INFO =
            "Requested to write span log, but Logs feature is not registered."
        internal const val TRACE_LOGGER_NAME = "trace"
    }
}
