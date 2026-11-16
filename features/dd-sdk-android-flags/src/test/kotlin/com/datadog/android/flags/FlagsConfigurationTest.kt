/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.flags.internal.selection.FlagSelection
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
        assertThat(configuration.customFlagEndpoint).isNull()
        assertThat(configuration.gracefulModeEnabled).isTrue()
        assertThat(configuration.initializationTimeoutMs).isEqualTo(5_000L)
        assertThat(configuration.flagSelection).isNull()
        assertThat(configuration.responseProtection).isNull()
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
    fun `M set the flag selection W flagSelection() { called twice }`() {
        // Given
        val builder = FlagsConfiguration.Builder()
            .flagSelection(tags = listOf("web"), keyPatterns = listOf("web-*"))

        // When
        val configuration = builder
            .flagSelection(tags = listOf("mobile"), keyPatterns = emptyList())
            .build()

        // Then
        assertThat(configuration.flagSelection)
            .isEqualTo(FlagSelection(tags = listOf("mobile"), keyPatterns = emptyList()))
    }

    @Test
    fun `M copy the lists W flagSelection() { caller mutates the lists after the call }`() {
        // Given
        val tags = mutableListOf("mobile")
        val keyPatterns = mutableListOf("checkout-*")
        val builder = FlagsConfiguration.Builder().flagSelection(tags, keyPatterns)

        // When
        tags += "web"
        keyPatterns.clear()

        // Then
        assertThat(builder.build().flagSelection)
            .isEqualTo(FlagSelection(tags = listOf("mobile"), keyPatterns = listOf("checkout-*")))
    }

    @Test
    fun `M keep null lists as absent W flagSelection() { null lists }`() {
        // When
        val configuration = FlagsConfiguration.Builder()
            .flagSelection(tags = null, keyPatterns = listOf("checkout-*"))
            .build()

        // Then
        assertThat(configuration.flagSelection)
            .isEqualTo(FlagSelection(tags = null, keyPatterns = listOf("checkout-*")))
    }

    @Test
    fun `M preserve the flag selection W copy() { legacy parameters }`() {
        // Given
        val testedConfiguration = FlagsConfiguration.Builder()
            .flagSelection(tags = listOf("mobile"), keyPatterns = listOf("checkout-*"))
            .build()

        // When
        val copiedConfiguration = testedConfiguration.copy(trackExposures = false)

        // Then
        assertThat(copiedConfiguration.flagSelection).isEqualTo(testedConfiguration.flagSelection)
    }

    // endregion

    // region Response protection

    @Test
    fun `M set the response protection W responseProtection()`() {
        // Given
        val protection = ResponseProtection.tlsPinning(
            ResponseProtectionMode.REQUIRED,
            TlsPinningProfile.DATADOG_STAGING_V1
        )

        // When
        val configuration = FlagsConfiguration.Builder()
            .responseProtection(protection)
            .build()

        // Then
        assertThat(configuration.responseProtection).isEqualTo(protection)
    }

    @Test
    fun `M preserve the response protection W copy() { legacy parameters }`(
        @BoolForgery fakeTrackExposures: Boolean
    ) {
        // Given
        val testedConfiguration = FlagsConfiguration.Builder()
            .responseProtection(
                ResponseProtection.tlsPinning(ResponseProtectionMode.REQUIRED, TlsPinningProfile.DATADOG_STAGING_V1)
            )
            .build()

        // When
        val copiedConfiguration = testedConfiguration.copy(trackExposures = fakeTrackExposures)

        // Then
        assertThat(copiedConfiguration.responseProtection).isEqualTo(testedConfiguration.responseProtection)
    }

    @Test
    fun `M compare the response protection W equals()`() {
        // Given
        val pins = listOf(
            "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "sha256/AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
        )
        fun build(origin: String) = FlagsConfiguration.Builder()
            .responseProtection(
                ResponseProtection.tlsPinning(
                    ResponseProtectionMode.REQUIRED,
                    TlsPinningProfile.custom(origin, TlsPinTarget.LEAF, pins)
                )
            )
            .build()

        // When / Then
        assertThat(build("https://a.example.com")).isEqualTo(build("https://a.example.com"))
        assertThat(build("https://a.example.com")).isNotEqualTo(build("https://b.example.com"))
        assertThat(build("https://a.example.com")).isNotEqualTo(FlagsConfiguration.Builder().build())
    }

    @Test
    fun `M copy the pins W TlsPinningProfile_custom() { caller mutates the list after the call }`() {
        // Given
        val pins = mutableListOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")

        // When
        val profile = TlsPinningProfile.custom("https://a.example.com", TlsPinTarget.LEAF, pins)
        pins.add("sha256/AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=")

        // Then
        assertThat(profile.pins).containsExactly("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
    }

    // endregion
}
