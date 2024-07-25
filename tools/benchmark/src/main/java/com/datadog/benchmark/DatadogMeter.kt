/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark

import com.datadog.benchmark.exporter.DatadogMetricExporter
import com.datadog.benchmark.internal.reader.CPUVitalReader
import com.datadog.benchmark.internal.reader.FpsVitalReader
import com.datadog.benchmark.internal.reader.MemoryVitalReader
import com.datadog.benchmark.internal.reader.VitalReader
import com.datadog.benchmark.noop.NoOpObservableDoubleGauge
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableDoubleGauge
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import java.util.concurrent.TimeUnit

/**
 * This class is responsible for managing the performance gauges related to CPU, FPS, and memory usage.
 * It provides functionalities to start and stop monitoring these metrics, which will be uploaded to
 * Datadog metric API.
 */
class DatadogMeter private constructor(private val meter: Meter) {

    private val cpuVitalReader: CPUVitalReader = CPUVitalReader()
    private val memoryVitalReader: MemoryVitalReader = MemoryVitalReader()
    private val fpsVitalReader: FpsVitalReader = FpsVitalReader()

    private val gaugesByMetricName: MutableMap<String, ObservableDoubleGauge> = mutableMapOf()

    /**
     * Starts cpu, memory and fps gauges.
     */
    fun startGauges() {
        startGauge(cpuVitalReader)
        startGauge(memoryVitalReader)
        startGauge(fpsVitalReader)
    }

    /**
     * Stops cpu, memory and fps gauges.
     */
    fun stopAllGauges() {
        stopGauge(cpuVitalReader)
        stopGauge(memoryVitalReader)
        stopGauge(fpsVitalReader)
    }

    private fun startGauge(reader: VitalReader) {
        synchronized(reader) {
            // Close the gauge if it exists before.
            val metricName = reader.metricName()
            gaugesByMetricName[metricName]?.close()
            reader.start()
            meter.gaugeBuilder(metricName).apply {
                reader.unit()?.let { unit ->
                    setUnit(unit)
                }
            }.buildWithCallback { observableDoubleMeasurement ->
                reader.readVitalData()?.let { data ->
                    observableDoubleMeasurement.record(data)
                }
            }.also { observableDoubleGauge ->
                gaugesByMetricName[metricName] = observableDoubleGauge
            }
        }
    }

    private fun stopGauge(reader: VitalReader) {
        synchronized(reader) {
            reader.stop()
            gaugesByMetricName[reader.metricName()]?.close()
            gaugesByMetricName[reader.metricName()] = NoOpObservableDoubleGauge()
        }
    }

    companion object {

        /**
         * Creates an instance of [DatadogMeter] with given configuration.
         */
        fun create(datadogExporterConfiguration: DatadogExporterConfiguration): DatadogMeter {
            val sdkMeterProvider = SdkMeterProvider.builder()
                .registerMetricReader(
                    PeriodicMetricReader.builder(DatadogMetricExporter(datadogExporterConfiguration))
                        .setInterval(datadogExporterConfiguration.intervalInSeconds, TimeUnit.SECONDS)
                        .build()
                )
                .build()

            val openTelemetry: OpenTelemetry = OpenTelemetrySdk.builder()
                .setMeterProvider(sdkMeterProvider)
                .build()
            val meter = openTelemetry.getMeter(METER_INSTRUMENTATION_SCOPE_NAME)
            return DatadogMeter(meter)
        }

        private const val METER_INSTRUMENTATION_SCOPE_NAME = "datadog.open-telemetry"
    }
}
