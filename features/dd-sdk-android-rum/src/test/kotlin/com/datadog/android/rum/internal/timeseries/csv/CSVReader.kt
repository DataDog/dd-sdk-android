/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.csv

import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.internal.timeseries.DataPoint
import com.datadog.android.rum.internal.timeseries.provider.DataPointsReader

/**
 * A test-only [DataPointsReader] that replays pre-recorded samples from a CSV string.
 *
 * The CSV is expected to contain a header line plus rows in the form:
 * `timestamp_nanos,metric_name,value`. Rows whose `metric_name` does not match the [metric]
 * filter are skipped, as are blank or malformed rows.
 */
internal class CSVReader(
    csvContent: String,
    metric: String,
    timeProvider: TimeProvider,
    override val intervalMs: Long = DEFAULT_INTERVAL_MS
) : DataPointsReader<Double>(timeProvider) {

    private val samples: List<DataPoint<Double>> = parseCsv(csvContent, metric)
    private var index: Int = 0

    fun hasNext(): Boolean = index < samples.size

    /** Total number of samples available for the metric. */
    val size: Int get() = samples.size

    override fun read(): DataPoint<Double> {
        check(hasNext()) { "CSVReader exhausted" }
        return samples[index++]
    }

    override fun readValue(): Double = error("CSVReader overrides read(); readValue() must not be called")

    private fun parseCsv(content: String, metric: String): List<DataPoint<Double>> {
        return content.lineSequence()
            .drop(1)
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val cols = trimmed.split(",")
                if (cols.size != COLUMN_COUNT) return@mapNotNull null
                if (cols[METRIC_INDEX] != metric) return@mapNotNull null
                val ts = cols[TIMESTAMP_INDEX].toLongOrNull() ?: return@mapNotNull null
                val v = cols[VALUE_INDEX].toDoubleOrNull() ?: return@mapNotNull null
                DataPoint(timestampNs = ts, value = v)
            }
            .toList()
    }

    companion object {
        const val DEFAULT_INTERVAL_MS: Long = 1_000L
        private const val COLUMN_COUNT = 3
        private const val TIMESTAMP_INDEX = 0
        private const val METRIC_INDEX = 1
        private const val VALUE_INDEX = 2
    }
}
