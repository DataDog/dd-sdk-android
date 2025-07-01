/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.macrobenchmark

import android.util.Log
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.core.configuration.BackPressureMitigation
import com.datadog.android.core.configuration.BackPressureStrategy
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration
import com.datadog.android.trace.opentelemetry.OtelTracerProvider
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerProvider
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.ContextPropagators
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TracerBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    lateinit var sdkCore: SdkCore

    lateinit var tracer: Tracer

    @Before
    fun setup() {
        sdkCore = Datadog.initialize(
            InstrumentationRegistry.getInstrumentation().context,
            createDatadogConfiguration(),
            TrackingConsent.GRANTED
        )!!

        val tracesConfig = TraceConfiguration.Builder().build()

        Trace.enable(tracesConfig, sdkCore)

        val tracerProvider = OtelTracerProvider.Builder(sdkCore).build()

        val otel = object : OpenTelemetry {
            override fun getTracerProvider(): TracerProvider {
                return tracerProvider
            }

            override fun getPropagators(): ContextPropagators {
                return ContextPropagators.noop()
            }
        }

        tracer = otel.getTracer("mega_tracer")
    }

    @Test
    fun log() {
        benchmarkRule.measureRepeated {
            val rootSpan = tracer.spanBuilder("trace_benchmark_span").startSpan()
            doChildWork(rootSpan, 2, 3, 3)
            rootSpan.end()
        }
    }

    private fun BenchmarkRule.Scope.doChildWork(parent: Span, childrenCount: Int, fullDepth: Int, depthLeft: Int) {
        if (depthLeft == 0) {
            return
        }

        repeat(childrenCount) {
            val spanName = "MegaSpan"
            val span = tracer.spanBuilder(spanName)
                .setParent(Context.current().with(parent))
                .startSpan()

            runWithTimingDisabled {
                Thread.sleep(10)
            }

            doChildWork(span, childrenCount, fullDepth, depthLeft - 1)

            span.end()
        }
    }
}

private fun createDatadogConfiguration(): Configuration {
    val configBuilder = Configuration.Builder(
        clientToken = BuildConfig.BENCHMARK_CLIENT_TOKEN,
        env = BuildConfig.BUILD_TYPE,
        service = "com.datadog.sample.benchmark"
    )
        .setBatchSize(BatchSize.SMALL)
        .setUploadFrequency(UploadFrequency.FREQUENT)

    configBuilder.setBackpressureStrategy(
        BackPressureStrategy(
            1024,
            { Log.w("BackPressure", "Threshold reached") },
            { Log.e("BackPressure", "Item dropped: $it") },
            BackPressureMitigation.IGNORE_NEWEST
        )
    )

    return configBuilder.build()
}
