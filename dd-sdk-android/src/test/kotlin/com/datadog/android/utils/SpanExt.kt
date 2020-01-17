package com.datadog.android.utils

import com.datadog.android.utils.forge.SpanForgeryFactory
import datadog.opentracing.DDSpan

fun DDSpan.copy(): DDSpan {
    return SpanForgeryFactory
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
}
