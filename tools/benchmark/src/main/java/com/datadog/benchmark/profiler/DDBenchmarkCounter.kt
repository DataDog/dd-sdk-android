/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.profiler

import com.datadog.android.internal.profiler.BenchmarkCounter
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.LongCounter

/**
 * Implementation of [BenchmarkCounter] for internal benchmarking.
 * @param counter The OpenTelemetry [LongCounter] instance to be used for recording values.
 */
class DDBenchmarkCounter(
    private val counter: LongCounter
) : BenchmarkCounter {
    override fun add(value: Long, attributes: Map<String, String>) {
        val attributesMap = Attributes.empty().toBuilder()
        attributes.forEach {
            attributesMap.put(it.key, it.value)
        }
        counter.add(value, attributesMap.build())
    }
}
