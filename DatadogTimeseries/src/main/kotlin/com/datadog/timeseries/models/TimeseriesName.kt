package com.datadog.timeseries.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TimeseriesName(val value: String) {
    @SerialName("memory_usage")
    MEMORY_USAGE("memory_usage"),

    @SerialName("cpu_usage")
    CPU_USAGE("cpu_usage");
}
