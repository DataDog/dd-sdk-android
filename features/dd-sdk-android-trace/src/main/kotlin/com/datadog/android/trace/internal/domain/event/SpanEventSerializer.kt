/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.event

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.core.constraints.DataConstraints
import com.datadog.android.core.constraints.DatadogDataConstraints
import com.datadog.android.internal.utils.NULL_MAP_VALUE
import com.datadog.android.trace.internal.storage.ContextAwareSerializer
import com.datadog.android.trace.model.SpanEvent
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.util.Date

internal class SpanEventSerializer(
    private val internalLogger: InternalLogger,
    private val dataConstraints: DataConstraints = DatadogDataConstraints(internalLogger)
) : ContextAwareSerializer<SpanEvent> {

    // region Serializer

    override fun serialize(datadogContext: DatadogContext, model: SpanEvent): String {
        val span = sanitizeKeys(model).toJson()
        val spans = JsonArray(1)
        spans.add(span)

        val jsonObject = JsonObject()
        jsonObject.add(TAG_SPANS, spans)
        jsonObject.addProperty(TAG_ENV, datadogContext.env)

        return jsonObject.toString()
    }

    // endregion

    // region Internal

    private fun sanitizeKeys(model: SpanEvent): SpanEvent {
        val newUserObject = sanitizeUserAttributes(model.meta.usr)
        val newMetricsObject = sanitizeMetrics(model.metrics)
        return model.copy(meta = model.meta.copy(usr = newUserObject), metrics = newMetricsObject)
    }

    private fun sanitizeUserAttributes(usr: SpanEvent.Usr): SpanEvent.Usr {
        val transformedAttributes = dataConstraints.validateAttributes(
            usr.additionalProperties,
            META_USR_KEY_PREFIX
        )
            .mapValues {
                try {
                    toMetaString(it.value)
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    internalLogger.log(
                        level = InternalLogger.Level.ERROR,
                        targets = listOf(
                            InternalLogger.Target.USER,
                            InternalLogger.Target.TELEMETRY
                        ),
                        messageBuilder = {
                            "Error converting value for key ${it.key} to meta string, it will be dropped."
                        },
                        throwable = e
                    )
                    null
                }
            }
            .filterValues { it != null }
        return usr.copy(
            additionalProperties = transformedAttributes.toMutableMap()
        )
    }

    private fun sanitizeMetrics(metrics: SpanEvent.Metrics): SpanEvent.Metrics {
        val transformedMetrics = dataConstraints.validateAttributes(
            metrics.additionalProperties,
            METRICS_KEY_PREFIX
        )
        return metrics.copy(
            additionalProperties = transformedMetrics
        )
    }

    private fun toMetaString(element: Any?): String? {
        return when (element) {
            NULL_MAP_VALUE -> null
            null -> null
            is Date -> element.time.toString()
            is JsonPrimitive -> element.asString
            else -> element.toString()
        }
    }
    // endregion

    companion object {

        // PAYLOAD TAGS
        internal const val TAG_SPANS = "spans"
        internal const val TAG_ENV = "env"
        internal const val META_USR_KEY_PREFIX = "meta.usr"
        internal const val METRICS_KEY_PREFIX = "metrics"
    }
}
