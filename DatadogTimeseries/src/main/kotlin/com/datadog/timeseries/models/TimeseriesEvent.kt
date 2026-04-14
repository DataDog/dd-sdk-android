package com.datadog.timeseries.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TimeseriesEvent(
    @SerialName("_dd") val dd: DD,
    val application: Application,
    val date: Long,
    val session: Session,
    val source: String,
    val type: String,
    val service: String? = null,
    val version: String? = null,
    val timeseries: Timeseries
) {
    @Serializable
    data class DD(
        @SerialName("format_version") val formatVersion: Int
    )

    @Serializable
    data class Application(
        val id: String
    )

    @Serializable
    data class Session(
        val id: String,
        val type: String
    )

    @Serializable
    data class Timeseries(
        val id: String,
        val name: TimeseriesName,
        val start: Long,
        val end: Long,
        val data: List<DataPoint>
    )

    @Serializable
    data class DataPoint(
        val timestamp: Long,
        @SerialName("data_point_value") val dataPointValue: Double
    )
}
