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

@OptIn(ExperimentalRumApi::class)
internal class TimeseriesConfigurationTests {

    @Test
    fun `M collect all types W constructor() { all types passed }`() {
        // When
        val config = TimeseriesConfiguration(TimeseriesType.entries.toSet())

        // Then
        assertThat(config.enabledTypes).containsExactlyInAnyOrderElementsOf(TimeseriesType.entries.toList())
    }

    @ParameterizedTest
    @EnumSource(TimeseriesType::class)
    fun `M collect selected type W constructor()`(fakeType: TimeseriesType) {
        // When
        val config = TimeseriesConfiguration(setOf(fakeType))

        // Then
        assertThat(config.enabledTypes).containsExactly(fakeType)
    }

    @Test
    fun `M collect no types W constructor() { empty set }`() {
        // When
        val config = TimeseriesConfiguration(emptySet())

        // Then
        assertThat(config.enabledTypes).isEmpty()
    }
}
