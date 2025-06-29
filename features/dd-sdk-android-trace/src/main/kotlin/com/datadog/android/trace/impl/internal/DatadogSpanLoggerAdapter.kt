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
import com.datadog.android.log.LogAttributes
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanLogger
import io.opentracing.log.Fields

internal class DatadogSpanLoggerAdapter(
    private val sdkCore: FeatureSdkCore,
) : DatadogSpanLogger {

    override fun log(message: String, span: DatadogSpan) {
        sendLogEvent(span, mutableMapOf(Fields.EVENT to message))
    }

    override fun log(attributes: Map<String, Any>, span: DatadogSpan) {
        sendLogEvent(span, attributes.toMutableMap())
    }

    private fun sendLogEvent(
        span: DatadogSpan,
        fields: MutableMap<String, Any?>,
    ) {
        val logsFeature = sdkCore.getFeature(Feature.LOGS_FEATURE_NAME)

        if (logsFeature != null && fields.isNotEmpty()) {
            val message = fields.remove(Fields.MESSAGE)?.toString() ?: DEFAULT_EVENT_MESSAGE
            fields[LogAttributes.DD_TRACE_ID] = span.context().traceId.toHexString()
            fields[LogAttributes.DD_SPAN_ID] = span.context().spanId.toString()
            val timestamp = System.currentTimeMillis()
            logsFeature.sendEvent(
                mapOf(
                    "type" to "span_log",
                    "loggerName" to TRACE_LOGGER_NAME,
                    "message" to message,
                    "attributes" to fields,
                    "timestamp" to timestamp,
                    "logStatus" to Log.VERBOSE
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

    companion object {
        internal const val TRACE_LOGGER_NAME = "trace"
        internal const val DEFAULT_EVENT_MESSAGE = "Span event"
        internal const val MISSING_LOG_FEATURE_INFO = "Requested to write span log, but Logs feature is not registered."
    }
}
