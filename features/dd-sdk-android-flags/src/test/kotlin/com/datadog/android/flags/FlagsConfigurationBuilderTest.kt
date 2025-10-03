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
    fun `M use sensible defaults W build()`() {
        // When
        val flagsConfiguration = testedBuilder.build()

        // Then
        assertThat(flagsConfiguration.customEndpointUrl).isNull()
        assertThat(flagsConfiguration.flaggingProxyUrl).isNull()
    }

    @Test
    fun `M build configuration with custom endpoint W useCustomEndpoint() and build()`(
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") customEndpointUrl: String
    ) {
        // When
        val flagsConfiguration = testedBuilder.useCustomEndpoint(customEndpointUrl).build()

        // Then
        assertThat(flagsConfiguration.customEndpointUrl).isEqualTo(customEndpointUrl)
        assertThat(flagsConfiguration.flaggingProxyUrl).isNull()
    }

    @Test
    fun `M build configuration with flagging proxy W useFlaggingProxy() and build()`(
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") flaggingProxyUrl: String
    ) {
        // When
        val flagsConfiguration = testedBuilder.useFlaggingProxy(flaggingProxyUrl).build()

        // Then
        assertThat(flagsConfiguration.customEndpointUrl).isNull()
        assertThat(flagsConfiguration.flaggingProxyUrl).isEqualTo(flaggingProxyUrl)
    }

    @Test
    fun `M build configuration with all options W setAll() and build()`(
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") customEndpointUrl: String,
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") flaggingProxyUrl: String
    ) {
        // When
        val flagsConfiguration = testedBuilder
            .useCustomEndpoint(customEndpointUrl)
            .useFlaggingProxy(flaggingProxyUrl)
            .build()

        // Then
        assertThat(flagsConfiguration.customEndpointUrl).isEqualTo(customEndpointUrl)
        assertThat(flagsConfiguration.flaggingProxyUrl).isEqualTo(flaggingProxyUrl)
    }

    @Test
    fun `M build configuration with null custom endpoint W useCustomEndpoint(null) and build()`() {
        // When
        val flagsConfiguration = testedBuilder.useCustomEndpoint(null).build()

        // Then
        assertThat(flagsConfiguration.customEndpointUrl).isNull()
        assertThat(flagsConfiguration.flaggingProxyUrl).isNull()
    }

    @Test
    fun `M build configuration with null flagging proxy W useFlaggingProxy(null) and build()`() {
        // When
        val flagsConfiguration = testedBuilder.useFlaggingProxy(null).build()

        // Then
        assertThat(flagsConfiguration.customEndpointUrl).isNull()
        assertThat(flagsConfiguration.flaggingProxyUrl).isNull()
    }
}
