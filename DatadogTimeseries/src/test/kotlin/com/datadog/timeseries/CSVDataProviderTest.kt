package com.datadog.timeseries

import com.datadog.timeseries.dataprovider.CSVDataProvider
import com.datadog.timeseries.models.TimeseriesName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CSVDataProviderTest {

    @Test
    fun `reads filtered samples from CSV`() {
        val csv = """
            timestamp,metric,value
            1000000000,memory_usage,30000000
            1000000000,cpu_usage,12.5
            2000000000,memory_usage,31000000
            2000000000,cpu_usage,15.0
            3000000000,memory_usage,32000000
        """.trimIndent()

        val provider = CSVDataProvider(csv, TimeseriesName.MEMORY_USAGE)

        val s1 = provider.read()
        assertEquals(1000000000L, s1?.timestamp)
        assertEquals(30000000.0, s1?.value)

        val s2 = provider.read()
        assertEquals(2000000000L, s2?.timestamp)
        assertEquals(31000000.0, s2?.value)

        val s3 = provider.read()
        assertEquals(3000000000L, s3?.timestamp)
        assertEquals(32000000.0, s3?.value)

        assertNull(provider.read())
    }

    @Test
    fun `filters by CPU usage`() {
        val csv = """
            timestamp,metric,value
            1000000000,memory_usage,30000000
            1000000000,cpu_usage,12.5
            2000000000,cpu_usage,15.0
        """.trimIndent()

        val provider = CSVDataProvider(csv, TimeseriesName.CPU_USAGE)

        val s1 = provider.read()
        assertEquals(1000000000L, s1?.timestamp)
        assertEquals(12.5, s1?.value)

        val s2 = provider.read()
        assertEquals(2000000000L, s2?.timestamp)
        assertEquals(15.0, s2?.value)

        assertNull(provider.read())
    }

    @Test
    fun `returns null for empty CSV`() {
        val csv = "timestamp,metric,value\n"
        val provider = CSVDataProvider(csv, TimeseriesName.MEMORY_USAGE)
        assertNull(provider.read())
    }

    @Test
    fun `skips malformed rows`() {
        val csv = """
            timestamp,metric,value
            1000000000,memory_usage,30000000
            bad_row
            2000000000,memory_usage,31000000
        """.trimIndent()

        val provider = CSVDataProvider(csv, TimeseriesName.MEMORY_USAGE)

        val s1 = provider.read()
        assertEquals(1000000000L, s1?.timestamp)

        val s2 = provider.read()
        assertEquals(2000000000L, s2?.timestamp)

        assertNull(provider.read())
    }
}
