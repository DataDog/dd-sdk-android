/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal.domain.event

import com.datadog.android.core.internal.constraints.DataConstraints
import com.datadog.android.core.internal.constraints.DatadogDataConstraints
import com.datadog.android.core.internal.persistence.Serializer
import com.datadog.android.core.internal.utils.NULL_MAP_VALUE
import com.datadog.android.tracing.model.SpanEvent
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.util.Date

internal class SpanEventSerializer(
    private val envName: String,
    private val dataConstraints: DataConstraints = DatadogDataConstraints()
) : Serializer<SpanEvent> {

    // region Serializer

    override fun serialize(model: SpanEvent): String {
        val span = sanitizeKeys(model).toJson()
        val spans = JsonArray(1)
        spans.add(span)

        val jsonObject = JsonObject()
        jsonObject.add(TAG_SPANS, spans)
        jsonObject.addProperty(TAG_ENV, envName)

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
        ).mapValues { toMetaString(it.value) }.filterValues { it != null }
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
