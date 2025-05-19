/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.di.activity

import com.datadog.android.api.SdkCore
import com.datadog.android.trace.opentelemetry.OtelTracerProvider
import com.datadog.benchmark.sample.config.BenchmarkConfig
import com.datadog.benchmark.sample.config.SyntheticsRun
import com.datadog.benchmark.sample.config.SyntheticsScenario
import dagger.Module
import dagger.Provides
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerProvider
import io.opentelemetry.context.propagation.ContextPropagators

// TODO WAHAHA move to app
@Module
internal interface OpenTelemetryModule {
    companion object {
        @Provides
        @BenchmarkActivityScope
        fun provideOpenTelemetry(
            sdkCore: SdkCore,
            config: BenchmarkConfig
        ): OpenTelemetry {
            val isTracingEnabled = config.run == SyntheticsRun.Instrumented &&
                config.scenario == SyntheticsScenario.Trace

            val tracerProvider = when (isTracingEnabled) {
                true -> OtelTracerProvider.Builder(sdkCore).build()
                false -> TracerProvider.noop()
            }

            return object : OpenTelemetry {
                override fun getTracerProvider(): TracerProvider {
                    return tracerProvider
                }

                override fun getPropagators(): ContextPropagators {
                    return ContextPropagators.noop()
                }
            }
        }

        @Provides
        @BenchmarkActivityScope
        fun provideTracer(
            openTelemetry: OpenTelemetry
        ): Tracer {
            return openTelemetry.getTracer(BENCHMARK_APP_TRACER_INSTRUMENTATION_SCOPE_NAME)
        }
    }
}

private const val BENCHMARK_APP_TRACER_INSTRUMENTATION_SCOPE_NAME = "dd-sdk-android-benchmark"
