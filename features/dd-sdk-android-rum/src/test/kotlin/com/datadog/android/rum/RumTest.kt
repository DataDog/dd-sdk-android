/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.net.RumRequestFactory
import com.datadog.android.rum.tracking.NoOpTrackingStrategy
import com.datadog.android.rum.tracking.NoOpViewTrackingStrategy
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureScope
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.core.InternalSdkCore
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumTest {

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mock()
    }

    @Test
    fun `𝕄 register RUM feature 𝕎 enable()`(
        @StringForgery fakePackageName: String,
        @Forgery fakeRumConfiguration: RumConfiguration
    ) {
        // When
        Rum.enable(fakeRumConfiguration, mockSdkCore)

        // Then
        argumentCaptor<RumFeature> {
            verify(mockSdkCore).registerFeature(capture())

            lastValue.onInitialize(
                mockSdkCore,
                appContext = mock { whenever(it.packageName) doReturn fakePackageName }
            )
            assertThat(lastValue.sampleRate)
                .isEqualTo(fakeRumConfiguration.featureConfiguration.sampleRate)
            assertThat(lastValue.telemetrySampleRate)
                .isEqualTo(fakeRumConfiguration.featureConfiguration.telemetrySampleRate)
            assertThat(lastValue.telemetryConfigurationSampleRate)
                .isEqualTo(fakeRumConfiguration.featureConfiguration.telemetryConfigurationSampleRate)
            assertThat(lastValue.backgroundEventTracking)
                .isEqualTo(fakeRumConfiguration.featureConfiguration.backgroundEventTracking)
            assertThat(lastValue.trackFrustrations)
                .isEqualTo(fakeRumConfiguration.featureConfiguration.trackFrustrations)
            if (fakeRumConfiguration.featureConfiguration.viewTrackingStrategy != null) {
                assertThat(lastValue.viewTrackingStrategy)
                    .isEqualTo(fakeRumConfiguration.featureConfiguration.viewTrackingStrategy)
            } else {
                assertThat(lastValue.viewTrackingStrategy)
                    .isInstanceOf(NoOpViewTrackingStrategy::class.java)
            }
            if (fakeRumConfiguration.featureConfiguration.longTaskTrackingStrategy != null) {
                assertThat(lastValue.longTaskTrackingStrategy)
                    .isEqualTo(fakeRumConfiguration.featureConfiguration.longTaskTrackingStrategy)
            } else {
                assertThat(lastValue.longTaskTrackingStrategy)
                    .isInstanceOf(NoOpTrackingStrategy::class.java)
            }
            assertThat((lastValue.requestFactory as RumRequestFactory).customEndpointUrl)
                .isEqualTo(fakeRumConfiguration.featureConfiguration.customEndpointUrl)
        }
    }

    @Test
    fun `𝕄 enable RUM debugging 𝕎 enableDebugging(true)`() {
        // Given
        val mockRumScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumScope
        val mockRumFeature = mock<RumFeature>()
        whenever(mockRumScope.unwrap<RumFeature>()) doReturn mockRumFeature

        // When
        Rum.enableDebugging(true, mockSdkCore)

        // Then
        verify(mockRumFeature).enableDebugging()
    }

    @Test
    fun `𝕄 disable RUM debugging 𝕎 enableDebugging(false)`() {
        // Given
        val mockRumScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumScope
        val mockRumFeature = mock<RumFeature>()
        whenever(mockRumScope.unwrap<RumFeature>()) doReturn mockRumFeature

        // When
        Rum.enableDebugging(false, mockSdkCore)

        // Then
        verify(mockRumFeature).disableDebugging()
    }

    @Test
    fun `𝕄 log warn message 𝕎 enableDebugging() { no RUM feature registered }`(
        @BoolForgery enable: Boolean
    ) {
        // Given
        val mockInternalLogger = mock<InternalLogger>()
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn null

        // When
        Rum.enableDebugging(enable, mockSdkCore)

        // Then
        verify(mockInternalLogger).log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            Rum.RUM_DEBUG_RUM_NOT_ENABLED_WARNING
        )
    }
}
