package com.datadog.android.utils

import com.datadog.android.utils.forge.SpanForgeryFactory
import datadog.opentracing.DDSpan

fun DDSpan.copy(): DDSpan {
    val ddSpan = SpanForgeryFactory
        .generateSpanBuilder(
            operationName,
            spanType,
            resourceName,
            serviceName,
            isError,
            tags
        )
        .withStartTimestamp(startTime)
        .start()
    metrics.forEach {
        ddSpan.context().setMetric(it.key, it.value)
    }
    meta.forEach {
        ddSpan.context().baggageItems.putIfAbsent(it.key, it.value)
    }
    return ddSpan
}
