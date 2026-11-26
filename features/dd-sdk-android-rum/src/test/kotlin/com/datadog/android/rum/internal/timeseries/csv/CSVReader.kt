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
 * `timestamp_nanos,metric_name,value[,value...]`. Rows whose `metric_name` does not match the
 * [metric] filter are skipped, as are blank rows and rows that [parseValue] rejects.
 */
internal class CSVReader<T : Any>(
    csvContent: String,
    metric: String,
    timeProvider: TimeProvider,
    override val intervalMs: Long = DEFAULT_INTERVAL_MS,
    private val parseValue: (List<String>) -> T?
) : DataPointsReader<T>(timeProvider) {

    private val samples: List<DataPoint<T>> = parseCsv(csvContent, metric)
    private var index: Int = 0

    fun hasNext(): Boolean = index < samples.size

    override fun read(): DataPoint<T> {
        check(hasNext()) { "CSVReader exhausted" }
        return samples[index++]
    }

    override fun readValue(): T = error("CSVReader overrides read(); readValue() must not be called")

    private fun parseCsv(content: String, metric: String): List<DataPoint<T>> {
        return content.lineSequence()
            .drop(1)
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val cols = trimmed.split(",")
                if (cols.size <= VALUES_START_INDEX) return@mapNotNull null
                if (cols[METRIC_INDEX] != metric) return@mapNotNull null
                val ts = cols[TIMESTAMP_INDEX].toLongOrNull() ?: return@mapNotNull null
                val v = parseValue(cols.drop(VALUES_START_INDEX)) ?: return@mapNotNull null
                DataPoint(deviceTimestampNs = ts, value = v)
            }
            .toList()
    }

    companion object {
        const val DEFAULT_INTERVAL_MS: Long = 1_000L
        private const val TIMESTAMP_INDEX = 0
        private const val METRIC_INDEX = 1
        private const val VALUES_START_INDEX = 2
    }
}
