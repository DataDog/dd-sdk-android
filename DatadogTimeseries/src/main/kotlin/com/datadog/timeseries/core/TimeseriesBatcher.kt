package com.datadog.timeseries.core

import com.datadog.timeseries.models.Sample

class TimeseriesBatcher(private val batchSize: Int = 30) {

    private val buffer = mutableListOf<Sample>()

    fun add(sample: Sample) {
        buffer.add(sample)
    }

    fun shouldFlush(): Boolean = buffer.size >= batchSize

    fun flush(): List<Sample> {
        val batch = buffer.toList()
        buffer.clear()
        return batch
    }

    fun flushRemaining(): List<Sample>? {
        if (buffer.isEmpty()) return null
        return flush()
    }
}
