package com.datadog.timeseries.dataprovider

import com.datadog.timeseries.models.Sample
import com.datadog.timeseries.models.TimeseriesName

class CSVDataProvider(
    csvContent: String,
    metric: TimeseriesName
) : DataProvider {

    private val samples: List<Sample>
    private var index: Int = 0

    init {
        val parsed = mutableListOf<Sample>()
        val lines = csvContent.lines()

        for (line in lines.drop(1)) { // skip header
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val columns = trimmed.split(",")
            if (columns.size != 3) continue

            if (columns[1] != metric.value) continue
            val timestamp = columns[0].toLongOrNull() ?: continue
            val value = columns[2].toDoubleOrNull() ?: continue

            parsed.add(Sample(timestamp = timestamp, value = value))
        }

        samples = parsed
    }

    override fun read(): Sample? {
        if (index >= samples.size) return null
        return samples[index++]
    }
}
