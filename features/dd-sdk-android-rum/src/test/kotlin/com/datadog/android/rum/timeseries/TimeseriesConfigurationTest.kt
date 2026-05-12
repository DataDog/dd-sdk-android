/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.timeseries

import com.datadog.android.rum.ExperimentalRumApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalRumApi::class)
internal class TimeseriesConfigurationTest {

    @Test
    fun `M create thread named datadog-timeseries W executorFactory()`() {
        // Given
        val config = TimeseriesConfiguration()
        val executor = config.executorFactory()
        val capturedName = arrayOfNulls<String>(1)
        val latch = CountDownLatch(1)

        // When
        executor.submit {
            capturedName[0] = Thread.currentThread().name
            latch.countDown()
        }
        latch.await(5, TimeUnit.SECONDS)
        executor.shutdownNow()

        // Then
        assertThat(capturedName[0]).isEqualTo("datadog-timeseries")
    }

    @Test
    fun `M create daemon thread W executorFactory()`() {
        // Given
        val config = TimeseriesConfiguration()
        val executor = config.executorFactory()
        val capturedDaemon = booleanArrayOf(false)
        val latch = CountDownLatch(1)

        // When
        executor.submit {
            capturedDaemon[0] = Thread.currentThread().isDaemon
            latch.countDown()
        }
        latch.await(5, TimeUnit.SECONDS)
        executor.shutdownNow()

        // Then
        assertThat(capturedDaemon[0]).isTrue()
    }

    @Test
    fun `M use defaults W constructed with no args`() {
        // When
        val config = TimeseriesConfiguration()

        // Then
        assertThat(config.bufferSize).isEqualTo(TimeseriesConfiguration.DEFAULT_BUFFER_SIZE)
        assertThat(config.intervalMs).isEqualTo(TimeseriesConfiguration.DEFAULT_INTERVAL_MS)
    }

    @Test
    fun `M use default bufferSize W constructed with non-positive bufferSize`() {
        // When
        val configZero = TimeseriesConfiguration(bufferSize = 0)
        val configNegative = TimeseriesConfiguration(bufferSize = -1)

        // Then
        assertThat(configZero.bufferSize).isEqualTo(TimeseriesConfiguration.DEFAULT_BUFFER_SIZE)
        assertThat(configNegative.bufferSize).isEqualTo(TimeseriesConfiguration.DEFAULT_BUFFER_SIZE)
    }

    @Test
    fun `M use default intervalMs W constructed with intervalMs below minimum`() {
        // When
        val configBelowMin = TimeseriesConfiguration(intervalMs = TimeseriesConfiguration.MIN_INTERVAL_MS - 1)
        val configZero = TimeseriesConfiguration(intervalMs = 0L)

        // Then
        assertThat(configBelowMin.intervalMs).isEqualTo(TimeseriesConfiguration.DEFAULT_INTERVAL_MS)
        assertThat(configZero.intervalMs).isEqualTo(TimeseriesConfiguration.DEFAULT_INTERVAL_MS)
    }

    @Test
    fun `M store provided values W constructed with valid args`() {
        // Given
        val fakeBufferSize = 10
        val fakeIntervalMs = 500L

        // When
        val config = TimeseriesConfiguration(bufferSize = fakeBufferSize, intervalMs = fakeIntervalMs)

        // Then
        assertThat(config.bufferSize).isEqualTo(fakeBufferSize)
        assertThat(config.intervalMs).isEqualTo(fakeIntervalMs)
    }

    @Test
    fun `M default collectInBackground to false W constructed with no args`() {
        // When
        val config = TimeseriesConfiguration()

        // Then
        assertThat(config.collectInBackground).isFalse()
    }

    @Test
    fun `M store collectInBackground W constructed with collectInBackground = true`() {
        // When
        val config = TimeseriesConfiguration(collectInBackground = true)

        // Then
        assertThat(config.collectInBackground).isTrue()
    }
}
