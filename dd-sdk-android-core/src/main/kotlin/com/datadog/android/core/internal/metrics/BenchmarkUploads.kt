/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.metrics

import com.datadog.android.internal.profiler.BenchmarkSdkUploads
import com.datadog.android.internal.profiler.GlobalBenchmark

internal class BenchmarkUploads(
    private val benchmarkSdkUploads: BenchmarkSdkUploads = GlobalBenchmark.getBenchmarkSdkUploads()
) {

    internal fun sendBenchmarkBytesUploaded(
        featureName: String,
        value: Long
    ) {
        sendBenchmarkUploads(
            featureName = featureName,
            metricName = BENCHMARK_BYTES_UPLOADED,
            value = value
        )
    }

    internal fun sendBenchmarkBytesDeleted(
        featureName: String,
        value: Long
    ) {
        sendBenchmarkUploads(
            featureName = featureName,
            metricName = BENCHMARK_BYTES_DELETED,
            value = value
        )
    }

    internal fun sendBenchmarkBytesWritten(
        featureName: String,
        value: Long
    ) {
        sendBenchmarkUploads(
            featureName = featureName,
            metricName = BENCHMARK_BYTES_WRITTEN,
            value = value
        )
    }

    internal fun incrementBenchmarkUploadsCount(
        featureName: String
    ) {
        sendBenchmarkUploads(
            featureName = featureName,
            metricName = BENCHMARK_UPLOAD_COUNT,
            value = 1
        )
    }

    private fun sendBenchmarkUploads(
        featureName: String,
        metricName: String,
        value: Long
    ) {
        val tags = mapOf(
            TRACK_NAME to featureName
        )

        benchmarkSdkUploads
            .getMeter(METER_NAME)
            .getCounter(metricName)
            .add(value, tags)
    }

    internal companion object {
        private const val TRACK_NAME = "track"
        private const val METER_NAME = "dd-sdk-android"
        internal const val BENCHMARK_BYTES_UPLOADED = "android.benchmark.bytes_uploaded"
        internal const val BENCHMARK_UPLOAD_COUNT = "android.benchmark.upload_count"
        internal const val BENCHMARK_BYTES_WRITTEN = "android.benchmark.bytes_written"
        internal const val BENCHMARK_BYTES_DELETED = "android.benchmark.bytes_deleted"
    }
}
