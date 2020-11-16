/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils

import com.datadog.android.utils.forge.SpanForgeryFactory
import com.datadog.opentracing.DDSpan

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
        .start() as DDSpan
    metrics.forEach {
        ddSpan.context().setMetric(it.key, it.value)
    }
    meta.forEach {
        ddSpan.context().baggageItems.putIfAbsent(it.key, it.value)
    }
    return ddSpan
}
