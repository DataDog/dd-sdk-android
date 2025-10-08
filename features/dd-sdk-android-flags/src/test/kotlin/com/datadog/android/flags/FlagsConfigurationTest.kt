/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
internal class FlagsConfigurationTest {

    @BoolForgery
    var fakeTrackExposures: Boolean = false

    // region Data Class Tests

    @Test
    fun `M create FlagsConfiguration W constructor { with all parameters }`() {
        // When
        val configuration = FlagsConfiguration(
            trackExposures = fakeTrackExposures
        )

        // Then
        assertThat(configuration.trackExposures).isEqualTo(fakeTrackExposures)
    }

    // region Builder Tests

    @Test
    fun `M have default values W Builder { constructor }`() {
        // When
        val builder = FlagsConfiguration.Builder()
        val configuration = builder.build()

        // Then
        assertThat(configuration.trackExposures).isTrue()
    }

    @Test
    fun `M set exposure logging enabled W Builder { setTrackExposures true }`() {
        // Given
        val builder = FlagsConfiguration.Builder()

        // When
        val result = builder.setTrackExposures(true)
        val configuration = builder.build()

        // Then
        assertThat(result).isSameAs(builder) // Fluent interface
        assertThat(configuration.trackExposures).isTrue()
    }

    @Test
    fun `M set exposure logging disabled W Builder { setTrackExposures false }`() {
        // Given
        val builder = FlagsConfiguration.Builder()

        // When
        val result = builder.setTrackExposures(false)
        val configuration = builder.build()

        // Then
        assertThat(result).isSameAs(builder) // Fluent interface
        assertThat(configuration.trackExposures).isFalse()
    }

    @Test
    fun `M build configuration with all options W Builder { fluent chaining }`() {
        // When
        val configuration = FlagsConfiguration.Builder()
            .setTrackExposures(true)
            .build()

        // Then
        assertThat(configuration.trackExposures).isTrue()
    }

    @Test
    fun `M build configuration with all options W Builder { reverse chaining order }`() {
        // When
        val configuration = FlagsConfiguration.Builder()
            .setTrackExposures(true)
            .build()

        // Then
        assertThat(configuration.trackExposures).isTrue()
    }

    @Test
    fun `M create multiple configurations W Builder { reusable builder }`() {
        // Given
        val builder = FlagsConfiguration.Builder()
            .setTrackExposures(true)

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
            .setTrackExposures(true)

        val firstConfiguration = builder.build()

        // When
        builder.setTrackExposures(false)
        val secondConfiguration = builder.build()

        // Then
        assertThat(firstConfiguration.trackExposures).isTrue()
        assertThat(secondConfiguration.trackExposures).isFalse()
    }

    // endregion
}
