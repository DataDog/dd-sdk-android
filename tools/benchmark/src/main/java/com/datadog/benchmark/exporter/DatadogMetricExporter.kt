package com.datadog.benchmark.exporter

import android.os.Build
import com.datadog.benchmark.DatadogExporterConfiguration
import com.datadog.benchmark.internal.DatadogHttpClient
import com.datadog.benchmark.internal.model.MetricContext
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.export.MetricExporter

internal class DatadogMetricExporter(datadogExporterConfiguration: DatadogExporterConfiguration) : MetricExporter {

    private val metricContext: MetricContext = MetricContext(
        deviceModel = Build.MODEL,
        osVersion = Build.VERSION.RELEASE,
        run = datadogExporterConfiguration.run ?: DEFAULT_RUN_NAME,
        scenario = datadogExporterConfiguration.scenario,
        applicationId = datadogExporterConfiguration.applicationId ?: DEFAULT_APPLICATION_ID,
        intervalInSeconds = datadogExporterConfiguration.intervalInSeconds
    )
    private val metricHttpClient: DatadogHttpClient = DatadogHttpClient(
        metricContext,
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
        private const val DEFAULT_RUN_NAME = "unknown run"
        private const val DEFAULT_APPLICATION_ID = "unassigned application id"
    }
}
