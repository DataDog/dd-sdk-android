/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.content.Context
import android.os.Looper
import com.datadog.android.Datadog
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.sampling.RateBasedSampler
import com.datadog.android.rum.internal.CombinedRumSessionListener
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.scope.RumApplicationScope
import com.datadog.android.rum.internal.domain.scope.RumSessionScope
import com.datadog.android.rum.internal.monitor.DatadogRumMonitor
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.CoreFeatureTestConfiguration
import com.datadog.android.utils.config.InternalLoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.core.DatadogCore
import com.datadog.android.v2.core.NoOpSdkCore
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumMonitorBuilderTest {

    lateinit var testedBuilder: RumMonitor.Builder

    @Forgery
    lateinit var fakeConfig: Configuration.Feature.RUM

    @Mock
    lateinit var mockSdkCore: DatadogCore

    private lateinit var rumFeature: RumFeature

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.coreFeature) doReturn coreFeature.mockInstance
        whenever(mockSdkCore.contextProvider) doReturn mock()

        rumFeature = RumFeature(fakeConfig, coreFeature.mockInstance)
        rumFeature.onInitialize(mockSdkCore, appContext.mockInstance)
        whenever(mockSdkCore.rumFeature) doReturn rumFeature

        Datadog.globalSdkCore = mockSdkCore

        testedBuilder = RumMonitor.Builder()
    }

    @AfterEach
    fun `tear down`() {
        Datadog.globalSdkCore = NoOpSdkCore()
    }

    @Test
    fun `𝕄 builds a default RumMonitor 𝕎 build()`() {
        // When
        val monitor = testedBuilder.build()

        // Then
        check(monitor is DatadogRumMonitor)
        assertThat(monitor.rootScope).isInstanceOf(RumApplicationScope::class.java)
        assertThat(monitor.rootScope)
            .overridingErrorMessage(
                "Expecting root scope to have applicationId ${coreFeature.fakeRumApplicationId}"
            )
            .matches {
                (it as RumApplicationScope)
                    .getRumContext()
                    .applicationId == coreFeature.fakeRumApplicationId
            }
        assertThat(monitor.handler.looper).isSameAs(Looper.getMainLooper())
        assertThat(monitor.samplingRate).isEqualTo(fakeConfig.samplingRate)
        assertThat(monitor.backgroundTrackingEnabled).isEqualTo(fakeConfig.backgroundEventTracking)

        assertThat(monitor.telemetryEventHandler.sdkCore).isSameAs(mockSdkCore)

        val telemetrySampler = monitor.telemetryEventHandler.eventSampler
        check(telemetrySampler is RateBasedSampler)

        assertThat(telemetrySampler.sampleRate)
            .isEqualTo(rumFeature.telemetrySamplingRate / 100)
    }

    @Test
    fun `𝕄 builds a RumMonitor with custom sampling 𝕎 build()`(
        @FloatForgery(0f, 100f) samplingRate: Float
    ) {
        // When
        val monitor = testedBuilder
            .sampleRumSessions(samplingRate)
            .build()

        // Then
        check(monitor is DatadogRumMonitor)
        assertThat(monitor.rootScope).isInstanceOf(RumApplicationScope::class.java)
        assertThat(monitor.rootScope)
            .overridingErrorMessage(
                "Expecting root scope to have applicationId ${coreFeature.fakeRumApplicationId}"
            )
            .matches {
                (it as RumApplicationScope)
                    .getRumContext()
                    .applicationId == coreFeature.fakeRumApplicationId
            }
        assertThat(monitor.handler.looper).isSameAs(Looper.getMainLooper())
        assertThat(monitor.samplingRate).isEqualTo(samplingRate)
    }

    @Test
    fun `𝕄 builds a RumMonitor without session listener 𝕎 build()`() {
        // When
        val monitor = testedBuilder.build()

        // Then
        check(monitor is DatadogRumMonitor)
        assertThat(monitor.rootScope).isInstanceOf(RumApplicationScope::class.java)
        val appScope = monitor.rootScope as RumApplicationScope
        assertThat(appScope.activeSession).isInstanceOf(RumSessionScope::class.java)
        val sessionScope = appScope.activeSession as RumSessionScope
        assertThat(sessionScope.sessionListener).isSameAs(monitor.telemetryEventHandler)
    }

    @Test
    fun `𝕄 builds a RumMonitor with session callback 𝕎 setSessionListener() + build()`() {
        // When
        val mockListener: RumSessionListener = mock()
        val monitor = testedBuilder
            .setSessionListener(mockListener)
            .build()

        // Then
        check(monitor is DatadogRumMonitor)
        assertThat(monitor.rootScope).isInstanceOf(RumApplicationScope::class.java)
        val appScope = monitor.rootScope as RumApplicationScope
        assertThat(appScope.activeSession).isInstanceOf(RumSessionScope::class.java)
        val sessionScope = appScope.activeSession as RumSessionScope
        assertThat(sessionScope.sessionListener)
            .isInstanceOf(CombinedRumSessionListener::class.java)
        val sessionListenerWrapper = sessionScope.sessionListener as CombinedRumSessionListener
        assertThat(sessionListenerWrapper.listeners).containsExactlyInAnyOrder(
            mockListener,
            monitor.telemetryEventHandler
        )
    }

    @Test
    fun `𝕄 builds nothing 𝕎 build() and RumFeature is not initialized`() {
        // Given
        Datadog.globalSdkCore = NoOpSdkCore()

        // When
        val monitor = testedBuilder.build()

        // Then
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            RumMonitor.Builder.RUM_NOT_ENABLED_ERROR_MESSAGE + "\n" +
                Datadog.MESSAGE_SDK_INITIALIZATION_GUIDE
        )
        check(monitor is NoOpRumMonitor)
    }

    @Test
    fun `𝕄 builds nothing 𝕎 build() { rumApplicationId is null }`() {
        // Given
        whenever(coreFeature.mockInstance.rumApplicationId) doReturn null

        // When
        val monitor = testedBuilder.build()

        // Then
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            RumMonitor.Builder.INVALID_APPLICATION_ID_ERROR_MESSAGE
        )
        check(monitor is NoOpRumMonitor)
    }

    companion object {
        val appContext = ApplicationContextTestConfiguration(Context::class.java)
        val coreFeature = CoreFeatureTestConfiguration(appContext)
        val logger = InternalLoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger, appContext, coreFeature)
        }
    }
}
