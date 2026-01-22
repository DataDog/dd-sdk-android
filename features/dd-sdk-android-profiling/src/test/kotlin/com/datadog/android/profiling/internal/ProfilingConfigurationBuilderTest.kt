/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import com.datadog.android.profiling.ProfilingConfiguration
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class ProfilingConfigurationBuilderTest {

    private lateinit var testedBuilder: ProfilingConfiguration.Builder

    @BeforeEach
    fun `set up`() {
        testedBuilder = ProfilingConfiguration.Builder()
    }

    @Test
    fun `M use sensible defaults W build()`() {
        // When
        val configuration = testedBuilder.build()

        // Then
        assertThat(configuration.customEndpointUrl).isNull()
    }

    @Test
    fun `M build config with custom endpoint W useCustomEndpoint() and build()`(
        @StringForgery(regex = "https://[a-z]+\\.[a-z]+(/[a-z]+)*") endpoint: String
    ) {
        // When
        val configuration = testedBuilder
            .useCustomEndpoint(endpoint)
            .build()

        // Then
        assertThat(configuration.customEndpointUrl).isEqualTo(endpoint)
    }
}
