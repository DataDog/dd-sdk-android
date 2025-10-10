/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
internal class FlagsConfigurationTest {

    // region Builder Tests

    @Test
    fun `M have default values W Builder { constructor }`() {
        // When
        val builder = FlagsConfiguration.Builder()
        val configuration = builder.build()

        // Then
        assertThat(configuration.trackExposures).isTrue()
        assertThat(configuration.customExposureEndpoint).isNull()
    }

    @Test
    fun `M set track exposures W Builder`(@BoolForgery fakeTrackExposuresState: Boolean) {
        // Given
        val builder = FlagsConfiguration.Builder()

        // When
        builder.trackExposures(fakeTrackExposuresState)
        val configuration = builder.build()

        // Then
        assertThat(configuration.trackExposures).isEqualTo(fakeTrackExposuresState)
        assertThat(configuration.customExposureEndpoint).isNull()
    }

    @Test
    fun `M set custom exposure endpoint W Builder`(@StringForgery fakeCustomExposureEndpoint: String) {
        // Given
        val builder = FlagsConfiguration.Builder()

        // When
        builder.useCustomExposureEndpoint(fakeCustomExposureEndpoint)
        val configuration = builder.build()

        // Then
        assertThat(configuration.trackExposures).isTrue()
        assertThat(configuration.customExposureEndpoint).isEqualTo(fakeCustomExposureEndpoint)
    }

    @Test
    fun `M create multiple configurations W Builder { reusable builder }`(
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") fakeCustomExposureEndpoint: String
    ) {
        // Given
        val builder = FlagsConfiguration.Builder()
            .trackExposures(true)
            .useCustomExposureEndpoint(fakeCustomExposureEndpoint)

        // When
        val configuration1 = builder.build()
        val configuration2 = builder.build()

        // Then
        assertThat(configuration1).isEqualTo(configuration2)
        assertThat(configuration1).isNotSameAs(configuration2) // Different instances
    }

    @Test
    fun `M modify builder after build W Builder { builder state preserved }`() {
        // Given
        val builder = FlagsConfiguration.Builder()
            .trackExposures(true)

        val firstConfiguration = builder.build()

        // When
        builder.trackExposures(false)
        val secondConfiguration = builder.build()

        // Then
        assertThat(firstConfiguration.trackExposures).isTrue()
        assertThat(secondConfiguration.trackExposures).isFalse()
    }

    // endregion
}
