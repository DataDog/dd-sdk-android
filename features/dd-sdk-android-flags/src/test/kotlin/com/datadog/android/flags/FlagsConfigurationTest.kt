/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
internal class FlagsConfigurationTest {

    // region defaultConfiguration()

    @Test
    fun `M return default config W defaultConfiguration()`() {
        // When
        val config = FlagsConfiguration.defaultConfiguration()

        // Then
        assertThat(config.exposureProxyEndpoint).isNull()
        assertThat(config.flaggingProxyEndpoint).isNull()
        assertThat(config.enableExposureLogging).isFalse()
    }

    @Test
    fun `M return same instance W defaultConfiguration() {called multiple times}`() {
        // When
        val config1 = FlagsConfiguration.defaultConfiguration()
        val config2 = FlagsConfiguration.defaultConfiguration()

        // Then
        assertThat(config1).isSameAs(config2)
    }

    // endregion

    // region data class behavior

    @Test
    fun `M support equality W equals() {same values}`(
        @StringForgery fakeExposureEndpoint: String,
        @StringForgery fakeFlaggingEndpoint: String
    ) {
        // Given
        val config1 = FlagsConfiguration.Builder()
            .useExposureEndpoint(fakeExposureEndpoint)
            .useFlagEndpoint(fakeFlaggingEndpoint)
            .build()

        val config2 = FlagsConfiguration.Builder()
            .useExposureEndpoint(fakeExposureEndpoint)
            .useFlagEndpoint(fakeFlaggingEndpoint)
            .build()

        // Then
        assertThat(config1).isEqualTo(config2)
        assertThat(config1.hashCode()).isEqualTo(config2.hashCode())
    }

    @Test
    fun `M support inequality W equals() {different values}`(
        @StringForgery fakeExposureEndpoint1: String,
        @StringForgery fakeExposureEndpoint2: String
    ) {
        // Given
        val config1 = FlagsConfiguration.Builder()
            .useExposureEndpoint(fakeExposureEndpoint1)
            .build()

        val config2 = FlagsConfiguration.Builder()
            .useExposureEndpoint(fakeExposureEndpoint2)
            .build()

        // Then
        assertThat(config1).isNotEqualTo(config2)
    }

    // endregion

    // region Builder behavior

    @Test
    fun `M allow builder reuse W Builder {build multiple times}`(
        @StringForgery fakeEndpoint1: String,
        @StringForgery fakeEndpoint2: String
    ) {
        // Given
        val builder = FlagsConfiguration.Builder()
            .useExposureEndpoint(fakeEndpoint1)

        // When
        val config1 = builder.build()

        // Then - first build succeeds
        assertThat(config1.exposureProxyEndpoint).isEqualTo(fakeEndpoint1)

        // When - modify and build again
        val config2 = builder
            .useExposureEndpoint(fakeEndpoint2)
            .build()

        // Then - second build succeeds with new value
        assertThat(config2.exposureProxyEndpoint).isEqualTo(fakeEndpoint2)
        // And first config is unchanged (immutability)
        assertThat(config1.exposureProxyEndpoint).isEqualTo(fakeEndpoint1)
    }

    @Test
    fun `M override previous value W Builder {same setter called twice}`(
        @StringForgery fakeEndpoint1: String,
        @StringForgery fakeEndpoint2: String
    ) {
        // When
        val config = FlagsConfiguration.Builder()
            .useExposureEndpoint(fakeEndpoint1)
            .useExposureEndpoint(fakeEndpoint2)
            .build()

        // Then - last value wins
        assertThat(config.exposureProxyEndpoint).isEqualTo(fakeEndpoint2)
    }

    @Test
    fun `M maintain independent fields W Builder {set different fields}`(
        @StringForgery fakeExposureEndpoint: String,
        @StringForgery fakeFlaggingEndpoint: String
    ) {
        // When
        val config = FlagsConfiguration.Builder()
            .useExposureEndpoint(fakeExposureEndpoint)
            .useFlagEndpoint(fakeFlaggingEndpoint)
            .enableExposureLogging(true)
            .build()

        // Then
        assertThat(config.exposureProxyEndpoint).isEqualTo(fakeExposureEndpoint)
        assertThat(config.flaggingProxyEndpoint).isEqualTo(fakeFlaggingEndpoint)
        assertThat(config.enableExposureLogging).isTrue()
    }

    @Test
    fun `M set enableExposureLogging to true W Builder#enableExposureLogging() {true}`() {
        // When
        val config = FlagsConfiguration.Builder()
            .enableExposureLogging(true)
            .build()

        // Then
        assertThat(config.enableExposureLogging).isTrue()
    }

    @Test
    fun `M set enableExposureLogging to false W Builder#enableExposureLogging() {false}`() {
        // When
        val config = FlagsConfiguration.Builder()
            .enableExposureLogging(false)
            .build()

        // Then
        assertThat(config.enableExposureLogging).isFalse()
    }

    @Test
    fun `M default to false W Builder#build() {enableExposureLogging not set}`() {
        // When
        val config = FlagsConfiguration.Builder().build()

        // Then
        assertThat(config.enableExposureLogging).isFalse()
    }

    // endregion

    // region enableExposureLogging equality

    @Test
    fun `M support inequality W equals() {different enableExposureLogging values}`(
        @StringForgery fakeExposureEndpoint: String
    ) {
        // Given
        val config1 = FlagsConfiguration.Builder()
            .useExposureEndpoint(fakeExposureEndpoint)
            .enableExposureLogging(true)
            .build()

        val config2 = FlagsConfiguration.Builder()
            .useExposureEndpoint(fakeExposureEndpoint)
            .enableExposureLogging(false)
            .build()

        // Then
        assertThat(config1).isNotEqualTo(config2)
    }

    // endregion
}
