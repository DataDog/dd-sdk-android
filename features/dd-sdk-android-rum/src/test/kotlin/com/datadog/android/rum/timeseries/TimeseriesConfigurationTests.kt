/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.timeseries

import com.datadog.android.rum.ExperimentalRumApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

@OptIn(ExperimentalRumApi::class)
internal class TimeseriesConfigurationTests {

    @Test
    fun `M collect all types W build() { collectOnly not called }`() {
        // When
        val config = TimeseriesConfiguration.Builder().build()

        // Then
        assertThat(config.enabledTypes).containsExactlyInAnyOrderElementsOf(TimeseriesType.values().toList())
    }

    @ParameterizedTest
    @EnumSource(TimeseriesType::class)
    fun `M collect selected type W collectOnly()`(fakeType: TimeseriesType) {
        // When
        val config = TimeseriesConfiguration.Builder()
            .collectOnly(fakeType)
            .build()

        // Then
        assertThat(config.enabledTypes).containsExactly(fakeType)
    }

    @Test
    fun `M collect no types W collectOnly() { empty array }`() {
        // When
        val config = TimeseriesConfiguration.Builder()
            .collectOnly(*emptyArray())
            .build()

        // Then
        assertThat(config.enabledTypes).isEmpty()
    }

    @Test
    fun `M default collectInBackground to false W build() { collectInBackground not called }`() {
        // When
        val config = TimeseriesConfiguration.Builder().build()

        // Then
        assertThat(config.collectInBackground).isFalse()
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M store collectInBackground W collectInBackground()`(fakeCollectInBackground: Boolean) {
        // When
        val config = TimeseriesConfiguration.Builder()
            .collectInBackground(fakeCollectInBackground)
            .build()

        // Then
        assertThat(config.collectInBackground).isEqualTo(fakeCollectInBackground)
    }
}
