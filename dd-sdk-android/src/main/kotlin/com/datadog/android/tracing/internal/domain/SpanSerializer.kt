package com.datadog.android.tracing.internal.domain

import com.datadog.android.core.internal.domain.Serializer
import com.google.gson.JsonObject
import datadog.opentracing.DDSpan
import java.lang.Long

internal class SpanSerializer : Serializer<DDSpan> {

    override fun serialize(model: DDSpan): String {
        val jsonObject = JsonObject()
        // it is save to convert to Long here from BigInteger...they are actually parsing this as
        // Long also on server side.
        jsonObject.addProperty(TRACE_ID_KEY, Long.toHexString(model.traceId.toLong()))
        jsonObject.addProperty(SPAN_ID_KEY, Long.toHexString(model.spanId.toLong()))
        jsonObject.addProperty(PARENT_ID_KEY, Long.toHexString(model.parentId.toLong()))
        jsonObject.addProperty(RESOURCE_KEY, model.resourceName)
        jsonObject.addProperty(OPERATION_NAME_KEY, model.operationName)
        jsonObject.addProperty(SERVICE_NAME_KEY, model.serviceName)
        jsonObject.addProperty(DURATION_KEY, model.durationNano)
        jsonObject.addProperty(START_TIMESTAMP_KEY, model.startTime)
        jsonObject.addProperty(TYPE_KEY, "object") // do not know yet what should be here
        addMeta(jsonObject, model)
        addMetrics(jsonObject, model)
        return jsonObject.toString()
    }

    private fun addMeta(jsonObject: JsonObject, model: DDSpan) {
        val metaObject = JsonObject()
        model.meta.forEach {
            metaObject.addProperty(it.key, it.value)
        }
        jsonObject.add(META_KEY, metaObject)
    }

    private fun addMetrics(jsonObject: JsonObject, model: DDSpan) {
        val metricsObject = JsonObject()
        model.metrics.forEach {
            metricsObject.addProperty(it.key, it.value)
        }
        // add special keys
        // we need this for sampling priority
        metricsObject.addProperty(METRICS_KEY_SAMPLING, 1)
        // same magic thing for sampling
        metricsObject.addProperty(METRICS_KEY_TOP_LEVEL, 1)
        jsonObject.add(METRICS_KEY, metricsObject)
    }

    companion object {
        const val START_TIMESTAMP_KEY = "start"
        const val DURATION_KEY = "duration"
        const val SERVICE_NAME_KEY = "service"
        const val TRACE_ID_KEY = "trace_id"
        const val SPAN_ID_KEY = "span_id"
        const val PARENT_ID_KEY = "parent_id"
        const val RESOURCE_KEY = "resource"
        const val OPERATION_NAME_KEY = "name"
        const val TYPE_KEY = "type"
        const val META_KEY = "meta"
        const val METRICS_KEY = "metrics"
        const val METRICS_KEY_TOP_LEVEL = "_top_level"
        const val METRICS_KEY_SAMPLING = "_sampling_priority"
    }

    companion object {
        const val START_TIMESTAMP_KEY = "start"
        const val DURATION_KEY = "duration"
        const val SERVICE_NAME_KEY = "service"
        const val TRACE_ID_KEY = "trace_id"
        const val SPAN_ID_KEY = "span_id"
        const val PARENT_ID_KEY = "parent_id"
        const val RESOURCE_KEY = "resource"
        const val OPERATION_NAME_KEY = "name"
    }
}
