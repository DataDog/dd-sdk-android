package com.datadog.benchmark.exporter

import android.os.Build
import com.datadog.android.BuildConfig
import com.datadog.benchmark.DatadogExporterConfiguration
import com.datadog.benchmark.internal.DatadogHttpClient
import com.datadog.benchmark.internal.model.BenchmarkContext
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.export.MetricExporter

internal class DatadogMetricExporter(datadogExporterConfiguration: DatadogExporterConfiguration) : MetricExporter {

    private val benchmarkContext: BenchmarkContext = BenchmarkContext(
        deviceModel = Build.MODEL ?: UNKNOWN_TAG_VALUE,
        osVersion = Build.VERSION.RELEASE ?: UNKNOWN_TAG_VALUE,
        run = datadogExporterConfiguration.run ?: UNKNOWN_TAG_VALUE,
        scenario = datadogExporterConfiguration.scenario,
        applicationId = datadogExporterConfiguration.applicationId ?: UNKNOWN_TAG_VALUE,
        intervalInSeconds = datadogExporterConfiguration.intervalInSeconds,
        env = BuildConfig.BUILD_TYPE
    )
    private val metricHttpClient: DatadogHttpClient = DatadogHttpClient(
        benchmarkContext,
        datadogExporterConfiguration
    )

    override fun getAggregationTemporality(instrumentType: InstrumentType): AggregationTemporality {
        return AggregationTemporality.DELTA
    }

    override fun export(metrics: MutableCollection<MetricData>): CompletableResultCode {
        // currently no aggregation is required
        metricHttpClient.uploadMetric(metrics.toList())
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
        private const val UNKNOWN_TAG_VALUE = "unknown"
    }
}
