/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.bridge.internal

import com.datadog.android.bridge.DdTrace
import io.opentracing.Span
import io.opentracing.util.GlobalTracer
import java.util.concurrent.TimeUnit

internal class BridgeTrace : DdTrace {

    private val spanMap: MutableMap<String, Span> = mutableMapOf()

    override fun startSpan(operation: String, timestamp: Long, context: Map<String, Any?>): String {
        val span = GlobalTracer.get().buildSpan(operation)
            .withStartTimestamp(TimeUnit.MILLISECONDS.toMicros(timestamp))
            .start()
        val spanContext = span.context()

        context.forEach {
            val value = it.value
            when (value) {
                is Boolean -> span.setTag(it.key, value)
                is Number -> span.setTag(it.key, value)
                is String -> span.setTag(it.key, value)
                else -> span.setTag(it.key, value?.toString())
            }
        }
        val spanId = spanContext.toSpanId()
        spanMap[spanId] = span
        return spanId
    }

    override fun finishSpan(spanId: String, timestamp: Long, context: Map<String, Any?>) {
        val span = spanMap.remove(spanId) ?: return
        context.forEach {
            val value = it.value
            when (value) {
                is Boolean -> span.setTag(it.key, value)
                is Number -> span.setTag(it.key, value)
                is String -> span.setTag(it.key, value)
                else -> span.setTag(it.key, value?.toString())
            }
        }
        span.finish(TimeUnit.MILLISECONDS.toMicros(timestamp))
    }
}
