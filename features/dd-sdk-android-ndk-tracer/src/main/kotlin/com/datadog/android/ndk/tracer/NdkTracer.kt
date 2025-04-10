/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ndk.tracer

import android.util.Log
import java.io.File
import kotlin.system.measureNanoTime

class NdkTracer(private val metricsCollectorDirectory:File? = null) {

    private val tracerPointer: Long?
    private val startSpanMetricsCollector = MetricsCollector("startSpan", metricsCollectorDirectory)
    private val finishSpanMetricsCollector = MetricsCollector("finishSpan", metricsCollectorDirectory)

    init {
        tracerPointer = createTracer()
    }

    fun dumpMetrics() {
        startSpanMetricsCollector.dumpMetrics()
        finishSpanMetricsCollector.dumpMetrics()
    }

    fun startSpan(spanName: String, parentId: String? = null): NdkSpan? {
        if (tracerPointer == null) {
            Log.v("NdkTracer", "Tracer instance is null")
            return null
        }
        var spanId: String? = null
        startSpanMetricsCollector.measureMethodDuration {
            spanId = nativeStartSpan(tracerPointer, spanName, parentId)
        }
        startSpanMetricsCollector.printMean()
        return spanId?.let {
            NdkSpan(tracerPointer, this, it, parentId)
        } ?: run {
            Log.v("NdkTracer", "Failed to start span")
            null
        }
    }

    fun finishSpan(span: NdkSpan): Boolean {
        if (tracerPointer == null) {
            Log.v("NdkTracer", "Tracer instance is null")
            return false
        }
        var toReturn: Boolean = false
        finishSpanMetricsCollector.measureMethodDuration {
            toReturn = nativeFinishSpan(tracerPointer, span.spanId)
        }
        finishSpanMetricsCollector.printMean()
        return toReturn
    }

    fun consumeSpan(span: String) {
        Log.v("NdkTracer", "Span consumed: $span")
    }


    private fun createTracer(): Long? {
        if (!NdkTracerBootstrap.librariesLoaded) {
            Log.v("NdkTracer", "Native libraries not loaded")
            return null
        }
        val tracerPointer: Long
        measureNanoTime {
            tracerPointer = nativeCreateTracer()
        }.let {
            Log.v("NdkTracer", "Tracer instance was created in ${it.toMilliseconds()} ms")
        }
        return tracerPointer
    }


    private fun Long.toMilliseconds(): Double {
        return this / 1_000_000.0
    }

    // region Native methods
    private external fun nativeCreateTracer(): Long

    private external fun nativeStartSpan(
        tracerPointer: Long,
        operationName: String,
        parentId: String?
    ): String

    private external fun nativeFinishSpan(
        tracerPointer: Long,
        spanId: String
    ): Boolean

}