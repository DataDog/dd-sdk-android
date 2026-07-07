/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.timeseries

import com.datadog.android.rum.ExperimentalRumApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@OptIn(ExperimentalRumApi::class)
internal class TimeseriesConfigurationTest {

    @Test
    fun `M use defaults W built with no args`() {
        // When
        val config = TimeseriesConfiguration.Builder().build()

        // Then
        assertThat(config.bufferSize).isEqualTo(TimeseriesConfiguration.DEFAULT_BUFFER_SIZE)
        assertThat(config.intervalMs).isEqualTo(TimeseriesConfiguration.DEFAULT_INTERVAL_MS)
    }

    @Test
    fun `M use default bufferSize W setBufferSize with non-positive value`() {
        // When
        val configZero = TimeseriesConfiguration.Builder().setBufferSize(0).build()
        val configNegative = TimeseriesConfiguration.Builder().setBufferSize(-1).build()

        // Then
        assertThat(configZero.bufferSize).isEqualTo(TimeseriesConfiguration.DEFAULT_BUFFER_SIZE)
        assertThat(configNegative.bufferSize).isEqualTo(TimeseriesConfiguration.DEFAULT_BUFFER_SIZE)
    }

    @Test
    fun `M use default intervalMs W setIntervalMs below minimum`() {
        // When
        val configBelowMin = TimeseriesConfiguration.Builder()
            .setIntervalMs(TimeseriesConfiguration.MIN_INTERVAL_MS - 1)
            .build()
        val configZero = TimeseriesConfiguration.Builder().setIntervalMs(0L).build()

        // Then
        assertThat(configBelowMin.intervalMs).isEqualTo(TimeseriesConfiguration.DEFAULT_INTERVAL_MS)
        assertThat(configZero.intervalMs).isEqualTo(TimeseriesConfiguration.DEFAULT_INTERVAL_MS)
    }

    @Test
    fun `M store provided values W built with valid args`() {
        // Given
        val fakeBufferSize = 10
        val fakeIntervalMs = 500L

        // When
        val config = TimeseriesConfiguration.Builder()
            .setBufferSize(fakeBufferSize)
            .setIntervalMs(fakeIntervalMs)
            .build()

        // Then
        assertThat(config.bufferSize).isEqualTo(fakeBufferSize)
        assertThat(config.intervalMs).isEqualTo(fakeIntervalMs)
    }

    @Test
    fun `M default collectInBackground to false W built with no args`() {
        // When
        val config = TimeseriesConfiguration.Builder().build()

        // Then
        assertThat(config.collectInBackground).isFalse()
    }

    @Test
    fun `M store collectInBackground W setCollectInBackground true`() {
        // When
        val config = TimeseriesConfiguration.Builder().collectInBackground(true).build()

        // Then
        assertThat(config.collectInBackground).isTrue()
    }

    @Test
    fun `M default additionalAttributes to empty W built with no args`() {
        // When
        val config = TimeseriesConfiguration.Builder().build()

        // Then
        assertThat(config.additionalAttributes).isEmpty()
    }

    @Test
    fun `M store attribute W addAttribute`() {
        // When
        val config = TimeseriesConfiguration.Builder()
            .addAttribute("key1", "value1")
            .addAttribute("key2", "value2")
            .build()

        // Then
        assertThat(config.additionalAttributes).containsExactlyInAnyOrderEntriesOf(
            mapOf("key1" to "value1", "key2" to "value2")
        )
    }

    @Test
    fun `M overwrite value W addAttribute called twice with same key`() {
        // When
        val config = TimeseriesConfiguration.Builder()
            .addAttribute("key", "first")
            .addAttribute("key", "second")
            .build()

        // Then
        assertThat(config.additionalAttributes).containsExactlyEntriesOf(mapOf("key" to "second"))
    }

    @Test
    fun `M keep built attributes unchanged W addAttribute after build`() {
        // Given
        val builder = TimeseriesConfiguration.Builder()
            .addAttribute("key1", "value1")
        val config = builder.build()

        // When
        builder.addAttribute("key2", "value2")

        // Then
        assertThat(config.additionalAttributes).containsExactlyEntriesOf(mapOf("key1" to "value1"))
    }
}
