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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

@Extensions(
    ExtendWith(ForgeExtension::class)
)
internal class ProfilingConfigurationTest {

    @Test
    fun `M hold null custom endpoint by default W build()`() {
        // When
        val config = ProfilingConfiguration.Builder().build()

        // Then
        assertThat(config.customEndpointUrl).isNull()
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
    fun `M support copy semantics W data class copy()`(
        @StringForgery(regex = "https://[a-z]+\\.[a-z]+(/[a-z]+)*") endpoint: String
    ) {
        // Given
        val original = ProfilingConfiguration.Builder().build()

        // When
        val modified = original.copy(customEndpointUrl = endpoint)

        // Then
        assertThat(original.customEndpointUrl).isNull()
        assertThat(modified.customEndpointUrl).isEqualTo(endpoint)
    }
}
