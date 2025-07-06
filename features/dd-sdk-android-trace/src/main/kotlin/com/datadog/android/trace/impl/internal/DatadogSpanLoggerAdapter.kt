/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl.internal

import android.util.Log
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.internal.utils.loggableStackTrace
import com.datadog.android.log.LogAttributes
import com.datadog.android.trace.api.DatadogTracingConstants
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanLogger
import io.opentracing.log.Fields

internal class DatadogSpanLoggerAdapter(
    private val sdkCore: FeatureSdkCore
) : DatadogSpanLogger {

    override fun log(message: String, span: DatadogSpan) {
        val fields = mutableMapOf<String, Any>(DatadogTracingConstants.LogAttributes.EVENT to message)
        extractError(fields, span)
        sendLogEvent(fields, span)
    }

    override fun logErrorMessage(message: String, span: DatadogSpan) {
        val fields = mutableMapOf<String, Any>(
            DatadogTracingConstants.LogAttributes.MESSAGE to message,
            DatadogTracingConstants.LogAttributes.STATUS to Log.ERROR
        )
        extractError(fields, span)
        sendLogEvent(fields, span)
    }

    override fun log(throwable: Throwable, span: DatadogSpan) {
        val fields = mutableMapOf<String, Any>(DatadogTracingConstants.LogAttributes.ERROR_OBJECT to throwable)
        extractError(fields, span)
        sendLogEvent(fields, span)
    }

    override fun log(attributes: Map<String, Any>, span: DatadogSpan) {
        extractError(attributes.toMutableMap(), span)
        sendLogEvent(attributes.toMutableMap(), span)
    }

    private fun extractError(
        map: MutableMap<String, *>,
        span: DatadogSpan
    ) {
        val throwable = map.remove(DatadogTracingConstants.LogAttributes.ERROR_OBJECT) as? Throwable
        val kind = map.remove(LogAttributes.ERROR_KIND)
        val errorType = kind?.toString() ?: throwable?.javaClass?.name

        if (errorType != null) {
            val stackField = map.remove(DatadogTracingConstants.LogAttributes.STACK)
            val msgField = map[LogAttributes.MESSAGE]
            val stack = stackField?.toString() ?: throwable?.loggableStackTrace()
            val message = msgField?.toString() ?: throwable?.message

            span.isError = true
            span.setTag(DatadogTracingConstants.Tags.KEY_ERROR_TYPE, errorType)
            span.setTag(DatadogTracingConstants.Tags.KEY_ERROR_MSG, message)
            span.setTag(DatadogTracingConstants.Tags.KEY_ERROR_STACK, stack)
        }
    }

    private fun sendLogEvent(
        fields: MutableMap<String, Any>,
        span: DatadogSpan
    ) {
        val logsFeature = sdkCore.getFeature(Feature.LOGS_FEATURE_NAME)

        if (logsFeature != null && fields.isNotEmpty()) {
            val message = fields.remove(Fields.MESSAGE)?.toString() ?: DEFAULT_EVENT_MESSAGE
            val logStatus = fields[DatadogTracingConstants.LogAttributes.STATUS] ?: Log.VERBOSE
            fields[LogAttributes.DD_TRACE_ID] = span.context().traceId.toHexString()
            fields[LogAttributes.DD_SPAN_ID] = span.context().spanId.toString()
            val timestamp = System.currentTimeMillis()
            logsFeature.sendEvent(
                buildMap {
                    put("type", "span_log")
                    put("loggerName", TRACE_LOGGER_NAME)
                    put("message", message)
                    put("attributes", fields)
                    put("timestamp", timestamp)
                    put("logStatus", logStatus)
                }
            )
        } else if (logsFeature == null) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { MISSING_LOG_FEATURE_INFO }
            )
        }
    }

    companion object {
        internal const val TRACE_LOGGER_NAME = "trace"
        internal const val DEFAULT_EVENT_MESSAGE = "Span event"
        internal const val MISSING_LOG_FEATURE_INFO = "Requested to write span log, but Logs feature is not registered."
    }
}
