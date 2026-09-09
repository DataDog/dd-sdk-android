/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Call
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.mock

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
        assertThat(configuration.customFlagEndpoint).isNull()
        assertThat(configuration.gracefulModeEnabled).isTrue()
        assertThat(configuration.initializationTimeoutMs).isEqualTo(5_000L)
        assertThat(configuration.flagAssignmentsHttpClient).isNull()
        assertThat(configuration.flagAssignmentsHttpClientConfiguration).isNull()
    }

    @Test
    fun `M set custom values W Builder`(
        @BoolForgery fakeTrackExposuresState: Boolean,
        @StringForgery fakeCustomExposureEndpoint: String,
        @StringForgery fakeCustomFlagEndpoint: String
    ) {
        // Given
        val builder = FlagsConfiguration.Builder()

        // When
        builder.trackExposures(fakeTrackExposuresState)
            .useCustomExposureEndpoint(fakeCustomExposureEndpoint)
            .useCustomFlagEndpoint(fakeCustomFlagEndpoint)
        val configuration = builder.build()

        // Then
        assertThat(configuration.trackExposures).isEqualTo(fakeTrackExposuresState)
        assertThat(configuration.customExposureEndpoint).isEqualTo(fakeCustomExposureEndpoint)
        assertThat(configuration.customFlagEndpoint).isEqualTo(fakeCustomFlagEndpoint)
    }

    @Test
    fun `M create multiple configurations W Builder { reusable builder }`(
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") fakeCustomExposureEndpoint: String,
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") fakeCustomFlagEndpoint: String
    ) {
        // Given
        val builder = FlagsConfiguration.Builder()
            .trackExposures(true)
            .useCustomExposureEndpoint(fakeCustomExposureEndpoint)
            .useCustomFlagEndpoint(fakeCustomFlagEndpoint)

        // When
        val configuration1 = builder.build()
        val configuration2 = builder.build()

        // Then
        assertThat(configuration1).isEqualTo(configuration2)
        assertThat(configuration1).isNotSameAs(configuration2) // Different instances
    }

    @Test
    fun `M modify builder after build W Builder { }`(
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") fakeCustomExposureEndpoint: String,
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") fakeCustomFlagEndpoint: String
    ) {
        // Given
        val builder = FlagsConfiguration.Builder()
            .trackExposures(false)

        val firstConfiguration = builder.build()

        // When
        builder.trackExposures(true).useCustomExposureEndpoint(fakeCustomExposureEndpoint)
        val secondConfiguration = builder.build()

        builder.useCustomFlagEndpoint(fakeCustomFlagEndpoint)
        val thirdConfiguration = builder.build()

        // Then
        assertThat(firstConfiguration.trackExposures).isFalse()
        assertThat(firstConfiguration.customExposureEndpoint).isNull()
        assertThat(firstConfiguration.customFlagEndpoint).isNull()

        assertThat(secondConfiguration.trackExposures).isTrue()
        assertThat(secondConfiguration.customExposureEndpoint).isEqualTo(fakeCustomExposureEndpoint)
        assertThat(secondConfiguration.customFlagEndpoint).isNull()

        assertThat(thirdConfiguration.trackExposures).isTrue()
        assertThat(thirdConfiguration.customExposureEndpoint).isEqualTo(fakeCustomExposureEndpoint)
        assertThat(thirdConfiguration.customFlagEndpoint).isEqualTo(fakeCustomFlagEndpoint)
    }

    @Test
    fun `M set gracefulModeEnabled to true W Builder { gracefulModeEnabled(true) }`() {
        // Given
        val builder = FlagsConfiguration.Builder()

        // When
        builder.gracefulModeEnabled(true)
        val configuration = builder.build()

        // Then
        assertThat(configuration.gracefulModeEnabled).isTrue()
    }

    @Test
    fun `M set gracefulModeEnabled to false W Builder { gracefulModeEnabled(false) }`() {
        // Given
        val builder = FlagsConfiguration.Builder()

        // When
        builder.gracefulModeEnabled(false)
        val configuration = builder.build()

        // Then
        assertThat(configuration.gracefulModeEnabled).isFalse()
    }

    @Test
    fun `M chain gracefulModeEnabled W Builder { returns builder }`() {
        // Given
        val builder = FlagsConfiguration.Builder()

        // When
        val returnedBuilder = builder.gracefulModeEnabled(false)

        // Then
        assertThat(returnedBuilder).isSameAs(builder)
    }

    @Test
    fun `M set initialization timeout W initializationTimeout()`() {
        // When
        val testedConfiguration = FlagsConfiguration.Builder()
            .initializationTimeout(2_500L)
            .build()

        // Then
        assertThat(testedConfiguration.initializationTimeoutMs).isEqualTo(2_500L)
    }

    @Test
    fun `M disable initialization timeout W initializationTimeout() { non-positive value }`() {
        listOf(0L, -1L).forEach { timeoutMs ->
            // When
            val testedConfiguration = FlagsConfiguration.Builder()
                .initializationTimeout(timeoutMs)
                .build()

            // Then
            assertThat(testedConfiguration.initializationTimeoutMs).isNull()
        }
    }

    @Test
    fun `M preserve initialization timeout W copy() { legacy parameters }`() {
        // Given
        val testedConfiguration = FlagsConfiguration.Builder()
            .initializationTimeout(2_500L)
            .build()

        // When
        val copiedConfiguration = testedConfiguration.copy(trackExposures = false)

        // Then
        assertThat(copiedConfiguration.initializationTimeoutMs).isEqualTo(2_500L)
    }

    @Test
    fun `M set custom HTTP client W useCustomFlagAssignmentsHttpClient()`() {
        // Given
        val mockCallFactory = mock<Call.Factory>()

        // When
        val testedConfiguration = FlagsConfiguration.Builder()
            .useCustomFlagAssignmentsHttpClient(mockCallFactory)
            .build()

        // Then
        assertThat(testedConfiguration.flagAssignmentsHttpClient).isSameAs(mockCallFactory)
    }

    @Test
    fun `M preserve custom HTTP client W copy() { legacy parameters }`() {
        // Given
        val mockCallFactory = mock<Call.Factory>()
        val testedConfiguration = FlagsConfiguration.Builder()
            .useCustomFlagAssignmentsHttpClient(mockCallFactory)
            .build()

        // When
        val copiedConfiguration = testedConfiguration.copy(trackExposures = false)

        // Then
        assertThat(copiedConfiguration.flagAssignmentsHttpClient).isSameAs(mockCallFactory)
    }

    @Test
    fun `M set HTTP client configuration W configureFlagAssignmentsHttpClient()`() {
        // Given
        val httpClientConfiguration: OkHttpClient.Builder.() -> Unit = {}

        // When
        val testedConfiguration = FlagsConfiguration.Builder()
            .configureFlagAssignmentsHttpClient(httpClientConfiguration)
            .build()

        // Then
        assertThat(testedConfiguration.flagAssignmentsHttpClient).isNull()
        assertThat(testedConfiguration.flagAssignmentsHttpClientConfiguration)
            .isSameAs(httpClientConfiguration)
    }

    @Test
    fun `M preserve HTTP client configuration W copy() { legacy parameters }`() {
        // Given
        val httpClientConfiguration: OkHttpClient.Builder.() -> Unit = {}
        val testedConfiguration = FlagsConfiguration.Builder()
            .configureFlagAssignmentsHttpClient(httpClientConfiguration)
            .build()

        // When
        val copiedConfiguration = testedConfiguration.copy(trackExposures = false)

        // Then
        assertThat(copiedConfiguration.flagAssignmentsHttpClientConfiguration)
            .isSameAs(httpClientConfiguration)
    }

    @Test
    fun `M use last HTTP client option W configure and replace clients()`() {
        // Given
        val customCallFactory = mock<Call.Factory>()
        val httpClientConfiguration: OkHttpClient.Builder.() -> Unit = {}

        // When
        val replacementLast = FlagsConfiguration.Builder()
            .configureFlagAssignmentsHttpClient(httpClientConfiguration)
            .useCustomFlagAssignmentsHttpClient(customCallFactory)
            .build()
        val configurationLast = FlagsConfiguration.Builder()
            .useCustomFlagAssignmentsHttpClient(customCallFactory)
            .configureFlagAssignmentsHttpClient(httpClientConfiguration)
            .build()

        // Then
        assertThat(replacementLast.flagAssignmentsHttpClient).isSameAs(customCallFactory)
        assertThat(replacementLast.flagAssignmentsHttpClientConfiguration).isNull()
        assertThat(configurationLast.flagAssignmentsHttpClient).isNull()
        assertThat(configurationLast.flagAssignmentsHttpClientConfiguration)
            .isSameAs(httpClientConfiguration)
    }

    // endregion
}
