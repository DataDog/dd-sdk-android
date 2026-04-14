package com.datadog.timeseries

import com.datadog.timeseries.core.TimeseriesBatcher
import com.datadog.timeseries.core.TimeseriesConfig
import com.datadog.timeseries.core.TimeseriesEventBuilder
import com.datadog.timeseries.dataprovider.DataProvider
import com.datadog.timeseries.encoding.TimeseriesEncoder
import com.datadog.timeseries.models.TimeseriesName
import java.util.UUID

class TimeseriesPipeline(
    private val provider: DataProvider,
    private val config: TimeseriesConfig,
    private val metricName: TimeseriesName,
    private val batchSize: Int = 30
) {

    fun processAll(): List<String> {
        val batcher = TimeseriesBatcher(batchSize)
        val builder = TimeseriesEventBuilder(config)
        val encoder = TimeseriesEncoder()

        val results = mutableListOf<String>()

        var sample = provider.read()
        while (sample != null) {
            batcher.add(sample)
            if (batcher.shouldFlush()) {
                val batch = batcher.flush()
                val event = builder.build(
                    samples = batch,
                    name = metricName,
                    eventId = UUID.randomUUID().toString().lowercase()
                )
                results.add(encoder.encode(event))
            }
            sample = provider.read()
        }

        val remaining = batcher.flushRemaining()
        if (remaining != null) {
            val event = builder.build(
                samples = remaining,
                name = metricName,
                eventId = UUID.randomUUID().toString().lowercase()
            )
            results.add(encoder.encode(event))
        }

        return results
    }
}
