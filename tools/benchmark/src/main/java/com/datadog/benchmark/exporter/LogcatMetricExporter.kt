/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.exporter

import android.os.Build
import android.util.Log
import com.datadog.android.BuildConfig
import com.datadog.benchmark.DatadogExporterConfiguration
import com.datadog.benchmark.internal.MetricRequestBodyBuilder
import com.datadog.benchmark.internal.model.BenchmarkContext
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.export.MetricExporter

internal class LogcatMetricExporter(datadogExporterConfiguration: DatadogExporterConfiguration) : MetricExporter {

    private val benchmarkContext: BenchmarkContext = BenchmarkContext(
        deviceModel = Build.MODEL,
        osVersion = Build.VERSION.RELEASE,
        run = datadogExporterConfiguration.run ?: DEFAULT_RUN_NAME,
        scenario = datadogExporterConfiguration.scenario,
        applicationId = datadogExporterConfiguration.applicationId ?: DEFAULT_APPLICATION_ID,
        intervalInSeconds = datadogExporterConfiguration.intervalInSeconds,
        env = BuildConfig.BUILD_TYPE
    )

    override fun getAggregationTemporality(instrumentType: InstrumentType): AggregationTemporality {
        return AggregationTemporality.DELTA
    }

    override fun export(metrics: Collection<MetricData>): CompletableResultCode {
        MetricRequestBodyBuilder(benchmarkContext).build(metrics.toList()).apply {
            Log.i("LogsMetricExporter", this)
        }
        return CompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode {
        // currently do nothing
        return CompletableResultCode.ofSuccess()
    }

    override fun shutdown(): CompletableResultCode {
        // currently do nothing
        return CompletableResultCode.ofSuccess()
    }

    companion object {
        private const val DEFAULT_RUN_NAME = "log run"
        private const val DEFAULT_APPLICATION_ID = "unassigned application id"
    }
}
