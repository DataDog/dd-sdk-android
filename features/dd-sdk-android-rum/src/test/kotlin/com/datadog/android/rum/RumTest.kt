/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.os.Looper
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.sampling.RateBasedSampler
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.scope.RumApplicationScope
import com.datadog.android.rum.internal.monitor.DatadogRumMonitor
import com.datadog.android.rum.internal.monitor.NoOpAdvancedRumMonitor
import com.datadog.android.rum.internal.net.RumRequestFactory
import com.datadog.android.rum.tracking.NoOpTrackingStrategy
import com.datadog.android.rum.tracking.NoOpViewTrackingStrategy
import com.datadog.android.rum.utils.config.MainLooperTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.rum.utils.verifyLog
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
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
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumTest {

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mock()
        whenever(mockSdkCore.firstPartyHostResolver) doReturn mock()
        whenever(mockSdkCore.createSingleThreadExecutorService(any())) doReturn mock()
        whenever(mockSdkCore.createScheduledExecutorService(any())) doReturn mock()
    }

    @Test
    fun `M register RUM feature W enable()`(
        @StringForgery fakePackageName: String,
        @Forgery fakeRumConfiguration: RumConfiguration
    ) {
        // When
        Rum.enable(fakeRumConfiguration, mockSdkCore)

        // Then
        argumentCaptor<RumFeature> {
            verify(mockSdkCore).registerFeature(capture())

            lastValue.onInitialize(
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
            assertThat(lastValue.initialResourceIdentifier).isSameAs(
                fakeRumConfiguration.featureConfiguration.initialResourceIdentifier
            )
        }
    }

    @Test
    fun `M register RUM monitor W enable()`(
        @StringForgery fakePackageName: String,
        @Forgery fakeRumConfiguration: RumConfiguration
    ) {
        // Given
        whenever(mockSdkCore.registerFeature(any())) doAnswer {
            val feature = it.getArgument<RumFeature>(0)
            feature.onInitialize(
                appContext = mock { whenever(it.packageName) doReturn fakePackageName }
            )
        }
        // When
        Rum.enable(fakeRumConfiguration, mockSdkCore)

        // Then
        val monitor = GlobalRumMonitor.get(mockSdkCore)
        check(monitor is DatadogRumMonitor)
        assertThat(monitor.rootScope).isInstanceOf(RumApplicationScope::class.java)
        assertThat(monitor.rootScope)
            .overridingErrorMessage(
                "Expecting root scope to have applicationId ${fakeRumConfiguration.applicationId}"
            )
            .matches {
                (it as RumApplicationScope)
                    .getRumContext()
                    .applicationId == fakeRumConfiguration.applicationId
            }
        assertThat(monitor.handler.looper).isSameAs(Looper.getMainLooper())
        assertThat(monitor.sampleRate)
            .isEqualTo(fakeRumConfiguration.featureConfiguration.sampleRate)
        assertThat(monitor.backgroundTrackingEnabled)
            .isEqualTo(fakeRumConfiguration.featureConfiguration.backgroundEventTracking)

        assertThat(monitor.telemetryEventHandler.sdkCore).isSameAs(mockSdkCore)

        val telemetrySampler = monitor.telemetryEventHandler.eventSampler
        check(telemetrySampler is RateBasedSampler)

        assertThat(telemetrySampler.getSampleRate())
            .isEqualTo(fakeRumConfiguration.featureConfiguration.telemetrySampleRate)
        assertThat(monitor.initialResourceIdentifier)
            .isSameAs(fakeRumConfiguration.featureConfiguration.initialResourceIdentifier)
    }

    @Test
    fun `M register nothing W enable() { SDK instance doesn't implement InternalSdkCore }`(
        @Forgery fakeRumConfiguration: RumConfiguration
    ) {
        // Given
        val mockInternalLogger = mock<InternalLogger>()
        val wrongSdkCore = mock<FeatureSdkCore>()
        whenever(wrongSdkCore.internalLogger) doReturn mockInternalLogger

        // When
        Rum.enable(fakeRumConfiguration, wrongSdkCore)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            Rum.UNEXPECTED_SDK_CORE_TYPE
        )
        verify(mockSdkCore, never()).registerFeature(any())
        check(GlobalRumMonitor.get(mockSdkCore) is NoOpAdvancedRumMonitor)
    }

    @Test
    fun `M register nothing W build() { rumApplicationId is missing }`(
        @Forgery fakeRumConfiguration: RumConfiguration,
        @StringForgery(regex = "\\s*") fakeApplicationId: String
    ) {
        // Given
        val mockInternalLogger = mock<InternalLogger>()
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger

        // When
        Rum.enable(
            rumConfiguration = fakeRumConfiguration.copy(applicationId = fakeApplicationId),
            mockSdkCore
        )

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            Rum.INVALID_APPLICATION_ID_ERROR_MESSAGE
        )
        verify(mockSdkCore, never()).registerFeature(any())
        check(GlobalRumMonitor.get(mockSdkCore) is NoOpAdvancedRumMonitor)
    }

    @Test
    fun `M register nothing W build() { RUM feature already registered }`(
        @Forgery fakeRumConfiguration: RumConfiguration
    ) {
        // Given
        val mockInternalLogger = mock<InternalLogger>()
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mock()

        // When
        Rum.enable(fakeRumConfiguration, mockSdkCore)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            Rum.RUM_FEATURE_ALREADY_ENABLED
        )
        verify(mockSdkCore, never()).registerFeature(any())
    }

    companion object {
        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(mainLooper)
        }
    }
}
