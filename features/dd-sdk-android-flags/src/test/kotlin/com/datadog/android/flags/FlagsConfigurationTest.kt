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
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
        assertThat(configuration.assignmentRequestCallFactory).isNull()
        assertThat(configuration.assignmentRequestTimeoutMs).isZero()
        assertThat(configuration.assignmentRequestRetryCount).isZero()
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
    fun `M preserve assignment request settings W legacy copy overload`() {
        // Given
        val callFactory = mock<Call.Factory>()
        val configuration = FlagsConfiguration.Builder()
            .assignmentRequestCallFactory(callFactory)
            .assignmentRequestTimeout(2_500L)
            .assignmentRequestRetryCount(3)
            .build()

        // When
        val copiedConfiguration = configuration.copy(trackExposures = false)
        val defaultCopy = configuration.copy()
        val configurationWithDifferentLimits = FlagsConfiguration.Builder()
            .assignmentRequestTimeout(5_000L)
            .assignmentRequestRetryCount(4)
            .build()

        // Then
        assertThat(copiedConfiguration.trackExposures).isFalse()
        assertThat(copiedConfiguration.assignmentRequestCallFactory).isSameAs(callFactory)
        assertThat(copiedConfiguration.assignmentRequestTimeoutMs).isEqualTo(2_500L)
        assertThat(copiedConfiguration.assignmentRequestRetryCount).isEqualTo(3)
        assertThat(defaultCopy).isEqualTo(configuration)
        assertThat(defaultCopy.assignmentRequestCallFactory).isSameAs(callFactory)
        assertThat(configurationWithDifferentLimits).isNotEqualTo(configuration)
    }

    @Test
    fun `M reject negative assignment timeout W generated copy`() {
        // Given
        val configuration = FlagsConfiguration.Builder().build()

        // When + Then
        assertThatThrownBy {
            configuration.copy(assignmentRequestTimeoutMs = -1)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("assignmentRequestTimeoutMs must be greater than or equal to 0")
    }

    @Test
    fun `M reject invalid retry counts W generated copy`() {
        // Given
        val configuration = FlagsConfiguration.Builder().build()

        // When + Then
        assertThatThrownBy {
            configuration.copy(assignmentRequestRetryCount = -1)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("assignmentRequestRetryCount must be between 0 and 10")

        assertThatThrownBy {
            configuration.copy(assignmentRequestRetryCount = 11)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("assignmentRequestRetryCount must be between 0 and 10")
    }

    @Test
    fun `M accept assignment request boundaries W generated copy`() {
        // Given
        val configuration = FlagsConfiguration.Builder().build()

        // When
        val copiedConfiguration = configuration.copy(
            assignmentRequestTimeoutMs = 0,
            assignmentRequestRetryCount = 10
        )

        // Then
        assertThat(copiedConfiguration.assignmentRequestTimeoutMs).isZero()
        assertThat(copiedConfiguration.assignmentRequestRetryCount).isEqualTo(10)
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
    fun `M set assignment request limits W Builder`() {
        // When
        val configuration = FlagsConfiguration.Builder()
            .assignmentRequestTimeout(2_500L)
            .assignmentRequestRetryCount(3)
            .build()

        // Then
        assertThat(configuration.assignmentRequestTimeoutMs).isEqualTo(2_500L)
        assertThat(configuration.assignmentRequestRetryCount).isEqualTo(3)
    }

    @Test
    fun `M set assignment request call factory W Builder`() {
        // Given
        val callFactory = mock<Call.Factory>()

        // When
        val configuration = FlagsConfiguration.Builder()
            .assignmentRequestCallFactory(callFactory)
            .build()

        // Then
        assertThat(configuration.assignmentRequestCallFactory).isSameAs(callFactory)
    }

    @Test
    fun `M accept assignment request limit boundaries W Builder`() {
        // When
        val configuration = FlagsConfiguration.Builder()
            .assignmentRequestTimeout(0)
            .assignmentRequestRetryCount(10)
            .build()

        // Then
        assertThat(configuration.assignmentRequestTimeoutMs).isZero()
        assertThat(configuration.assignmentRequestRetryCount).isEqualTo(10)
    }

    @Test
    fun `M coerce negative assignment request timeout W Builder`() {
        // When
        val configuration = FlagsConfiguration.Builder()
            .assignmentRequestTimeout(-1)
            .build()

        // Then
        assertThat(configuration.assignmentRequestTimeoutMs).isZero()
    }

    @Test
    fun `M coerce assignment retry counts outside supported range W Builder`() {
        // When
        val belowRange = FlagsConfiguration.Builder()
            .assignmentRequestRetryCount(-1)
            .build()
        val aboveRange = FlagsConfiguration.Builder()
            .assignmentRequestRetryCount(11)
            .build()

        // Then
        assertThat(belowRange.assignmentRequestRetryCount).isZero()
        assertThat(aboveRange.assignmentRequestRetryCount).isEqualTo(10)
    }

    // endregion
}
