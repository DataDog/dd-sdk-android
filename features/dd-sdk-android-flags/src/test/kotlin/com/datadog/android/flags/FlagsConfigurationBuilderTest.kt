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
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(ForgeExtension::class),
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class FlagsConfigurationBuilderTest {

    private val testedBuilder: FlagsConfiguration.Builder = FlagsConfiguration.Builder()

    @Test
    fun `M use defaults W build()`() {
        // When
        val flagsConfiguration = testedBuilder.build()

        // Then
        assertThat(flagsConfiguration.customExposureEndpoint).isNull()
        assertThat(flagsConfiguration.customFlagEndpoint).isNull()
    }

    @Test
    fun `M build configuration with custom exposure endpoint W useCustomExposureEndpoint() and build()`(
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") customEndpointUrl: String
    ) {
        // When
        val flagsConfiguration = testedBuilder.useCustomExposureEndpoint(customEndpointUrl).build()

        // Then
        assertThat(flagsConfiguration.customExposureEndpoint).isEqualTo(customEndpointUrl)
        assertThat(flagsConfiguration.customFlagEndpoint).isNull()
    }

    @Test
    fun `M build configuration with custom flag endpoint W useCustomFlagEndpoint() and build()`(
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") customFlagEndpoint: String
    ) {
        // When
        val flagsConfiguration = testedBuilder.useCustomFlagEndpoint(customFlagEndpoint).build()

        // Then
        assertThat(flagsConfiguration.customExposureEndpoint).isNull()
        assertThat(flagsConfiguration.customFlagEndpoint).isEqualTo(customFlagEndpoint)
    }

    @Test
    fun `M build configuration with null custom exposure endpoint W useCustomExposureEndpoint(null) and build()`() {
        // When
        val flagsConfiguration = testedBuilder.useCustomExposureEndpoint(null).build()

        // Then
        assertThat(flagsConfiguration.customExposureEndpoint).isNull()
        assertThat(flagsConfiguration.customFlagEndpoint).isNull()
    }

    @Test
    fun `M build configuration with null custom flag endpoint W useCustomFlagEndpoint(null) and build()`() {
        // When
        val flagsConfiguration = testedBuilder.useCustomFlagEndpoint(null).build()

        // Then
        assertThat(flagsConfiguration.customExposureEndpoint).isNull()
        assertThat(flagsConfiguration.customFlagEndpoint).isNull()
    }
}
