package com.datadog.timeseries.core

data class TimeseriesConfig(
    val applicationId: String,
    val sessionId: String,
    val sessionType: String,
    val source: String,
    val service: String? = null,
    val version: String? = null
)
