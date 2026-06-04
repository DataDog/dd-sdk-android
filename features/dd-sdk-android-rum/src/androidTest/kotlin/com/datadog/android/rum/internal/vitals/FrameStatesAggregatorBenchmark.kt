/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.metrics.performance.FrameData
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.metrics.PerformanceMetric
import com.datadog.android.core.metrics.TelemetryMetricType
import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import com.datadog.android.rum.internal.domain.FrameMetricsData
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Benchmarks the iteration strategy in [FrameStatesAggregator.onFrame] (RUM-8785).
 *
 * The `onFrame` callback fires 60-120 times per second. The original `forEach {}` allocated
 * an [Iterator] on every call; the indexed `for` loop avoids this. With 2-3 delegates
 * this per-frame allocation was a measurable source of GC pressure.
 *
 * The [useForEachIteration] toggle is read from the instrumentation argument
 * `useForEachIteration` (default `false`), so baseline and regression runs
 * can be compared without recompilation:
 *
 * ```
 * -Pandroid.testInstrumentationRunnerArguments.useForEachIteration=false  # baseline
 * -Pandroid.testInstrumentationRunnerArguments.useForEachIteration=true   # regression
 * ```
 */
@RunWith(AndroidJUnit4::class)
class FrameStatesAggregatorBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val listeners: List<FrameStateListener> = List(LISTENER_COUNT) {
        object : FrameStateListener {
            @Suppress("EmptyFunctionBlock")
            override fun onFrame(volatileFrameData: FrameData) {}

            @Suppress("EmptyFunctionBlock")
            override fun onFrameMetricsData(data: FrameMetricsData) {}
        }
    }

    private val frameData = FrameData(
        frameStartNanos = 0L,
        frameDurationUiNanos = FRAME_16MS_NS,
        isJank = false,
        states = emptyList()
    )

    @Before
    fun setUp() {
        val args = InstrumentationRegistry.getArguments()
        useForEachIteration = args.getString("useForEachIteration", "false").toBoolean()
    }

    @After
    fun tearDown() {
        useForEachIteration = false
    }

    @Test
    fun onFrame() {
        val aggregator = FrameStatesAggregator(listeners, NoOpLogger)
        benchmarkRule.measureRepeated {
            aggregator.onFrame(frameData)
        }
    }

    companion object {
        private const val LISTENER_COUNT = 3
        private const val FRAME_16MS_NS = 16_666_666L

        private val NoOpLogger = object : InternalLogger {
            override fun log(
                level: InternalLogger.Level,
                target: InternalLogger.Target,
                messageBuilder: () -> String,
                throwable: Throwable?,
                onlyOnce: Boolean,
                additionalProperties: Map<String, Any?>?
            ) {}

            override fun log(
                level: InternalLogger.Level,
                targets: List<InternalLogger.Target>,
                messageBuilder: () -> String,
                throwable: Throwable?,
                onlyOnce: Boolean,
                additionalProperties: Map<String, Any?>?
            ) {}

            override fun logMetric(
                messageBuilder: () -> String,
                additionalProperties: Map<String, Any?>,
                samplingRate: Float,
                creationSampleRate: Float?
            ) {}

            override fun startPerformanceMeasure(
                callerClass: String,
                metric: TelemetryMetricType,
                samplingRate: Float,
                operationName: String
            ): PerformanceMetric? = null

            override fun logApiUsage(
                samplingRate: Float,
                apiUsageEventBuilder: () -> InternalTelemetryEvent.ApiUsage
            ) {}
        }
    }
}
