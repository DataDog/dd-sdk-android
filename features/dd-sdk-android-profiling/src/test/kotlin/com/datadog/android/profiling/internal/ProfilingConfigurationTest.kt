/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import com.datadog.android.profiling.ProfilingConfiguration
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

@Extensions(
    ExtendWith(ForgeExtension::class)
)
internal class ProfilingConfigurationTest {

    @Test
    fun `M hold default values W build()`() {
        // When
        val config = ProfilingConfiguration.Builder().build()

        // Then
        assertThat(config.customEndpointUrl).isNull()
        assertThat(config.sampleRate).isEqualTo(100f)
    }

    @Test
    fun `M hold provided custom endpoint W useCustomEndpoint()`(
        @StringForgery(regex = "https://[a-z]+\\.[a-z]+(/[a-z]+)*") endpoint: String
    ) {
        // When
        val config = ProfilingConfiguration.Builder()
            .useCustomEndpoint(endpoint)
            .build()

        // Then
        assertThat(config.customEndpointUrl).isEqualTo(endpoint)
    }

    @Test
    fun `M hold provided sample rate W setSampleRate()`(
        @FloatForgery(min = 0f, max = 100f) sampleRate: Float
    ) {
        // When
        val config = ProfilingConfiguration.Builder()
            .setSampleRate(sampleRate)
            .build()

        // Then
        assertThat(config.sampleRate).isEqualTo(sampleRate)
    }

    @Test
    fun `M support copy semantics W data class copy()`(
        @StringForgery(regex = "https://[a-z]+\\.[a-z]+(/[a-z]+)*") endpoint: String,
        @FloatForgery(min = 0f, max = 100f) sampleRate: Float
    ) {
        // Given
        val original = ProfilingConfiguration.Builder().build()

        // When
        val modified = original.copy(
            customEndpointUrl = endpoint,
            sampleRate = sampleRate
        )

        // Then
        assertThat(original.customEndpointUrl).isNull()
        assertThat(original.sampleRate).isEqualTo(100f)
        assertThat(modified.customEndpointUrl).isEqualTo(endpoint)
        assertThat(modified.sampleRate).isEqualTo(sampleRate)
    }
}
