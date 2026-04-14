package com.datadog.timeseries

import com.datadog.timeseries.core.TimeseriesBatcher
import com.datadog.timeseries.models.Sample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimeseriesBatcherTest {

    @Test
    fun `does not flush before batch size`() {
        val batcher = TimeseriesBatcher(batchSize = 3)
        batcher.add(Sample(timestamp = 1, value = 10.0))
        batcher.add(Sample(timestamp = 2, value = 20.0))

        assertFalse(batcher.shouldFlush())
    }

    @Test
    fun `flushes at batch size`() {
        val batcher = TimeseriesBatcher(batchSize = 3)
        batcher.add(Sample(timestamp = 1, value = 10.0))
        batcher.add(Sample(timestamp = 2, value = 20.0))
        batcher.add(Sample(timestamp = 3, value = 30.0))

        assertTrue(batcher.shouldFlush())

        val batch = batcher.flush()
        assertEquals(3, batch.size)
        assertEquals(1L, batch[0].timestamp)
        assertEquals(2L, batch[1].timestamp)
        assertEquals(3L, batch[2].timestamp)
    }

    @Test
    fun `flush clears buffer`() {
        val batcher = TimeseriesBatcher(batchSize = 2)
        batcher.add(Sample(timestamp = 1, value = 10.0))
        batcher.add(Sample(timestamp = 2, value = 20.0))

        batcher.flush()

        assertFalse(batcher.shouldFlush())
        assertTrue(batcher.flush().isEmpty())
    }

    @Test
    fun `flushRemaining returns samples`() {
        val batcher = TimeseriesBatcher(batchSize = 5)
        batcher.add(Sample(timestamp = 1, value = 10.0))
        batcher.add(Sample(timestamp = 2, value = 20.0))

        val remaining = batcher.flushRemaining()
        assertNotNull(remaining)
        assertEquals(2, remaining.size)
    }

    @Test
    fun `flushRemaining returns null when empty`() {
        val batcher = TimeseriesBatcher(batchSize = 5)
        assertNull(batcher.flushRemaining())
    }

    @Test
    fun `multiple batches`() {
        val batcher = TimeseriesBatcher(batchSize = 2)
        batcher.add(Sample(timestamp = 1, value = 10.0))
        batcher.add(Sample(timestamp = 2, value = 20.0))

        assertTrue(batcher.shouldFlush())
        val batch1 = batcher.flush()
        assertEquals(2, batch1.size)

        batcher.add(Sample(timestamp = 3, value = 30.0))
        batcher.add(Sample(timestamp = 4, value = 40.0))

        assertTrue(batcher.shouldFlush())
        val batch2 = batcher.flush()
        assertEquals(2, batch2.size)
        assertEquals(3L, batch2[0].timestamp)
    }
}
