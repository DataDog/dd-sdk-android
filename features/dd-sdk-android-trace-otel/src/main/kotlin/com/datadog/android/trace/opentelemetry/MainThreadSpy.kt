/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.opentelemetry

import android.os.Looper
import android.util.Printer
import io.opentelemetry.api.trace.Span

object MainThreadSpy {


    fun spyOnMainThread() {
        Looper.getMainLooper().setMessageLogging(object : Printer {
            val otelTracerProvider = OtelTracerProvider.Builder()
                .setService("datadog.android.profiling")
                .build()
            val otelTracer = otelTracerProvider.get("MainThreadSpy")
            var currentMessage: String? = null
            var isBefore: Boolean = false
            var currentSpan: Span? = null
            override fun println(x: String) {
                if (isBefore) {
                    currentMessage = x
                    currentSpan = otelTracer.spanBuilder(x).startSpan()
                }
                else{
                    currentSpan?.end()
                    currentSpan = null
                }
                isBefore = !isBefore
            }
        })
    }
}