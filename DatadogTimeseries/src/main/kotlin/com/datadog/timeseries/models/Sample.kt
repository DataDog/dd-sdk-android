package com.datadog.timeseries.models

/**
 * A single timestamped performance sample.
 * @property timestamp Timestamp in nanoseconds.
 * @property value Metric value (e.g. bytes for memory, percent for CPU).
 */
data class Sample(
    val timestamp: Long,
    val value: Double
)
