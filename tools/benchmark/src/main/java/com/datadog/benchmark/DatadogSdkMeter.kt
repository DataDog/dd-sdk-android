/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark

import com.datadog.android.internal.profiler.GlobalBenchmark
import com.datadog.benchmark.exporter.DatadogMetricExporter
import com.datadog.benchmark.exporter.DatadogSpanExporter
import com.datadog.benchmark.profiler.DDBenchmarkSdkPerformance
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import java.util.concurrent.TimeUnit

/**
 * This class is responsible for managing the measurements of SDK performance
 * for example, bytes written and bytes deleted.
 * This data will be uploaded to Datadog metric API.
 */
class DatadogSdkMeter private constructor(private val meter: Meter) : DatadogBaseMeter {

    override fun startMeasuring() {
        // for this class this is a noop
    }

    override fun stopMeasuring() {
        // for this class this is a noop
    }

    companion object {

        /**
         * Creates an instance of [DatadogSdkMeter] with the given configuration.
         */
        fun create(datadogExporterConfiguration: DatadogExporterConfiguration): DatadogSdkMeter {
            val datadogExporter = DatadogMetricExporter(datadogExporterConfiguration)
            val sdkMeterProvider = SdkMeterProvider.builder()
                .registerMetricReader(
                    PeriodicMetricReader.builder(datadogExporter)
                        .setInterval(datadogExporterConfiguration.intervalInSeconds, TimeUnit.SECONDS)
                        .build()
                )
                .build()
            val traceProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(DatadogSpanExporter(datadogExporterConfiguration)).build())
                .build()
            val openTelemetry: OpenTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(traceProvider)
                .setMeterProvider(sdkMeterProvider)
                .build()
            GlobalOpenTelemetry.set(openTelemetry)
            GlobalBenchmark.register(DDBenchmarkSdkPerformance())
            val meter = openTelemetry.getMeter(METER_INSTRUMENTATION_SCOPE_NAME)
            return DatadogSdkMeter(meter)
        }

        private const val METER_INSTRUMENTATION_SCOPE_NAME = "datadog.open-telemetry"
    }
}
