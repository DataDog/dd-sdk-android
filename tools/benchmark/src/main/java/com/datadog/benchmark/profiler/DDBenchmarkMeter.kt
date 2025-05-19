/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.profiler

import com.datadog.android.internal.profiler.BenchmarkCounter
import com.datadog.android.internal.profiler.BenchmarkMeter
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement
import java.util.function.Consumer

/**
 * Implementation of [BenchmarkMeter] for internal benchmarking.
 * @param meter The OpenTelemetry [Meter] instance to be used for recording metrics.
 */
class DDBenchmarkMeter(private val meter: Meter) : BenchmarkMeter {

    override fun getCounter(operation: String): BenchmarkCounter {
        return DDBenchmarkCounter(meter.counterBuilder(operation).build())
    }

    override fun createObservableGauge(
        metricName: String,
        tags: Map<String, String>,
        callback: () -> Double
    ) {
        val attributesMap = Attributes.empty().toBuilder()
        tags.forEach {
            attributesMap.put(it.key, it.value)
        }

        val observableCallback = (
            Consumer<ObservableDoubleMeasurement> { measurement ->
                measurement.record(callback(), attributesMap.build())
            }
            )

        meter.gaugeBuilder(metricName).buildWithCallback(observableCallback)
    }
}
