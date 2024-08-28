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
import com.datadog.benchmark.internal.BenchmarkSpanToSpanEventMapper
import com.datadog.benchmark.internal.DatadogHttpClient
import com.datadog.benchmark.internal.model.BenchmarkContext
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter

internal class DatadogSpanExporter(datadogExporterConfiguration: DatadogExporterConfiguration) : SpanExporter {

    private val benchmarkContext: BenchmarkContext = BenchmarkContext(
        deviceModel = Build.MODEL ?: UNKNOWN_TAG_VALUE,
        osVersion = Build.VERSION.RELEASE ?: UNKNOWN_TAG_VALUE,
        run = datadogExporterConfiguration.run ?: UNKNOWN_TAG_VALUE,
        scenario = datadogExporterConfiguration.scenario,
        applicationId = datadogExporterConfiguration.applicationId ?: UNKNOWN_TAG_VALUE,
        intervalInSeconds = datadogExporterConfiguration.intervalInSeconds,
        env = BuildConfig.BUILD_TYPE
    )

    private val httpClient: DatadogHttpClient = DatadogHttpClient(
        benchmarkContext,
        datadogExporterConfiguration
    )

    private val benchmarkSpanToSpanEventMapper = BenchmarkSpanToSpanEventMapper()

    override fun export(spans: MutableCollection<SpanData>): CompletableResultCode {
        Log.i("DatadogSpanExporter", "Span exported!")
        Log.i("DatadogSpanExporter", spans.toString())
        spans.map {
            benchmarkSpanToSpanEventMapper.map(benchmarkContext, it)
        }.apply {
            httpClient.uploadSpanEvent(this)
        }
        return CompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode {
        // do nothing
        return CompletableResultCode.ofSuccess()
    }

    override fun shutdown(): CompletableResultCode {
        // do nothing
        return CompletableResultCode.ofSuccess()
    }

    companion object {
        private const val UNKNOWN_TAG_VALUE = "unknown"
    }
}
