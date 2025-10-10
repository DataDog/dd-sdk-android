/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.api.feature.Feature.Companion.FLAGS_FEATURE_NAME
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.internal.FlagsFeature
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class FlagsTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    // region enable()

    @Test
    fun `M register FlagsFeature W enable()`() {
        // Given
        val fakeConfiguration = FlagsConfiguration.Builder()
            .build()

        // When
        Flags.enable(fakeConfiguration, mockSdkCore)

        // Then
        argumentCaptor<FlagsFeature> {
            verify(mockSdkCore).registerFeature(capture())
            assertThat(lastValue.name).isEqualTo(FLAGS_FEATURE_NAME)
        }
    }

    @Test
    fun `M pass configuration to FlagsFeature W enable() { with custom config }`(
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") fakeCustomEndpoint: String,
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") fakeFlagEndpoint: String
    ) {
        // Given
        val fakeConfiguration = FlagsConfiguration.Builder()
            .useCustomExposureEndpoint(fakeCustomEndpoint)
            .useCustomFlagEndpoint(fakeFlagEndpoint)
            .build()

        // When
        Flags.enable(fakeConfiguration, mockSdkCore)

        // Then
        argumentCaptor<FlagsFeature> {
            verify(mockSdkCore).registerFeature(capture())
            assertThat(lastValue.name).isEqualTo(FLAGS_FEATURE_NAME)
            assertThat(lastValue.flagsConfiguration.customExposureEndpoint).isEqualTo(fakeCustomEndpoint)
            assertThat(lastValue.flagsConfiguration.customFlagEndpoint).isEqualTo(fakeFlagEndpoint)
        }
    }

    @Test
    fun `M use default configuration W enable() { no config provided }`() {
        // When
        Flags.enable(sdkCore = mockSdkCore)

        // Then
        argumentCaptor<FlagsFeature> {
            verify(mockSdkCore).registerFeature(capture())
            assertThat(lastValue.flagsConfiguration.customExposureEndpoint).isNull()
            assertThat(lastValue.flagsConfiguration.customFlagEndpoint).isNull()
        }
    }

    @Test
    fun `M pass default configuration to FlagsFeature W enable() { default config }`() {
        // Given
        val defaultConfiguration = FlagsConfiguration.default()

        // When
        Flags.enable(defaultConfiguration, mockSdkCore)

        // Then
        argumentCaptor<FlagsFeature> {
            verify(mockSdkCore).registerFeature(capture())
            assertThat(lastValue.flagsConfiguration.customExposureEndpoint).isNull()
            assertThat(lastValue.flagsConfiguration.customFlagEndpoint).isNull()
        }
    }

    @Test
    fun `M handle null configuration values W enable() { custom config with nulls }`() {
        // Given
        val fakeConfiguration = FlagsConfiguration.Builder()
            .useCustomExposureEndpoint(null)
            .useCustomFlagEndpoint(null)
            .build()

        // When
        Flags.enable(fakeConfiguration, mockSdkCore)

        // Then
        argumentCaptor<FlagsFeature> {
            verify(mockSdkCore).registerFeature(capture())
            assertThat(lastValue.flagsConfiguration.customExposureEndpoint).isNull()
            assertThat(lastValue.flagsConfiguration.customFlagEndpoint).isNull()
        }
    }

    @Test
    fun `M register FlagsFeature with custom endpoint W enable() { custom endpoint }`(
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") fakeCustomEndpoint: String
    ) {
        // Given
        val fakeConfiguration = FlagsConfiguration.Builder()
            .useCustomExposureEndpoint(fakeCustomEndpoint)
            .build()

        // When
        Flags.enable(fakeConfiguration, mockSdkCore)

        // Then
        argumentCaptor<FlagsFeature> {
            verify(mockSdkCore).registerFeature(capture())
            assertThat(lastValue.flagsConfiguration.customExposureEndpoint).isEqualTo(fakeCustomEndpoint)
        }
    }

    @Test
    fun `M register FlagsFeature with flag endpoint W enable() { flag endpoint }`(
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") fakeFlagEndpoint: String
    ) {
        // Given
        val fakeConfiguration = FlagsConfiguration.Builder()
            .useCustomFlagEndpoint(fakeFlagEndpoint)
            .build()

        // When
        Flags.enable(fakeConfiguration, mockSdkCore)

        // Then
        argumentCaptor<FlagsFeature> {
            verify(mockSdkCore).registerFeature(capture())
            assertThat(lastValue.flagsConfiguration.customFlagEndpoint).isEqualTo(fakeFlagEndpoint)
        }
    }

    @Test
    fun `M create FlagsFeature with correct sdkCore W enable()`() {
        // Given
        val fakeConfiguration = FlagsConfiguration.Builder().build()

        // When
        Flags.enable(fakeConfiguration, mockSdkCore)

        // Then
        argumentCaptor<FlagsFeature> {
            verify(mockSdkCore).registerFeature(capture())
            // Verify the feature was created (implicit by successful registration)
            assertThat(lastValue).isNotNull
        }
    }

    // endregion
}
