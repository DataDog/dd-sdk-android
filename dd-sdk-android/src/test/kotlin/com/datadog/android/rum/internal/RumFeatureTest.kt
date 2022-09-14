/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import android.app.Application
import android.view.Choreographer
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.VitalsUpdateFrequency
import com.datadog.android.core.internal.SdkFeatureTest
import com.datadog.android.core.internal.event.NoOpEventMapper
import com.datadog.android.core.internal.thread.NoOpScheduledExecutorService
import com.datadog.android.rum.internal.domain.RumFilePersistenceStrategy
import com.datadog.android.rum.internal.tracking.NoOpUserActionTrackingStrategy
import com.datadog.android.rum.internal.tracking.UserActionTrackingStrategy
import com.datadog.android.rum.internal.vitals.AggregatingVitalMonitor
import com.datadog.android.rum.internal.vitals.NoOpVitalMonitor
import com.datadog.android.rum.internal.vitals.VitalFrameCallback
import com.datadog.android.rum.tracking.NoOpTrackingStrategy
import com.datadog.android.rum.tracking.NoOpViewTrackingStrategy
import com.datadog.android.rum.tracking.TrackingStrategy
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import com.datadog.android.utils.extension.mockChoreographerInstance
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.rum.internal.net.RumRequestFactory
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doNothing
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.concurrent.ScheduledThreadPoolExecutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
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
internal class RumFeatureTest : SdkFeatureTest<Any, Configuration.Feature.RUM, RumFeature>() {

    @Mock
    lateinit var mockChoreographer: Choreographer

    override fun createTestedFeature(): RumFeature {
        return RumFeature(coreFeature.mockInstance)
    }

    override fun forgeConfiguration(forge: Forge): Configuration.Feature.RUM {
        return forge.getForgery()
    }

    override fun featureDirName(): String {
        return "rum"
    }

    @BeforeEach
    fun `set up RUM`() {
        doNothing().whenever(mockChoreographer).postFrameCallback(any())
        mockChoreographerInstance(mockChoreographer)
    }

    @Test
    fun `𝕄 initialize persistence strategy 𝕎 initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.persistenceStrategy)
            .isInstanceOf(RumFilePersistenceStrategy::class.java)
    }

    @Test
    fun `𝕄 create a rum request factory 𝕎 createRequestFactory()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // When
        val requestFactory = testedFeature.createRequestFactory(fakeConfigurationFeature)

        // Then
        assertThat(requestFactory).isInstanceOf(RumRequestFactory::class.java)
    }

    @Test
    fun `𝕄 store sampling rate 𝕎 initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.samplingRate).isEqualTo(fakeConfigurationFeature.samplingRate)
    }

    @Test
    fun `𝕄 store telemetry sampling rate 𝕎 initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.telemetrySamplingRate)
            .isEqualTo(fakeConfigurationFeature.telemetrySamplingRate)
    }

    @Test
    fun `𝕄 store background tracking 𝕎 initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.backgroundEventTracking)
            .isEqualTo(fakeConfigurationFeature.backgroundEventTracking)
    }

    @Test
    fun `𝕄 store and register viewTrackingStrategy 𝕎 initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.viewTrackingStrategy)
            .isEqualTo(fakeConfigurationFeature.viewTrackingStrategy)
        verify(fakeConfigurationFeature.viewTrackingStrategy!!).register(appContext.mockInstance)
    }

    @Test
    fun `𝕄 store userActionTrackingStrategy 𝕎 initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.actionTrackingStrategy)
            .isEqualTo(fakeConfigurationFeature.userActionTrackingStrategy)
        verify(fakeConfigurationFeature.userActionTrackingStrategy!!)
            .register(appContext.mockInstance)
    }

    @Test
    fun `𝕄 store longTaskTrackingStrategy 𝕎 initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.longTaskTrackingStrategy)
            .isEqualTo(fakeConfigurationFeature.longTaskTrackingStrategy)
        verify(fakeConfigurationFeature.longTaskTrackingStrategy!!)
            .register(appContext.mockInstance)
    }

    @Test
    fun `𝕄 use noop viewTrackingStrategy 𝕎 initialize()`() {
        // Given
        val config = fakeConfigurationFeature.copy(viewTrackingStrategy = null)

        // When
        testedFeature.initialize(appContext.mockInstance, config)

        // Then
        assertThat(testedFeature.viewTrackingStrategy)
            .isInstanceOf(NoOpViewTrackingStrategy::class.java)
    }

    @Test
    fun `𝕄 use noop userActionTrackingStrategy 𝕎 initialize()`() {
        // Given
        val config = fakeConfigurationFeature.copy(userActionTrackingStrategy = null)

        // When
        testedFeature.initialize(appContext.mockInstance, config)

        // Then
        assertThat(testedFeature.actionTrackingStrategy)
            .isInstanceOf(NoOpUserActionTrackingStrategy::class.java)
    }

    @Test
    fun `𝕄 use noop longTaskTrackingStrategy 𝕎 initialize()`() {
        // Given
        val config = fakeConfigurationFeature.copy(longTaskTrackingStrategy = null)

        // When
        testedFeature.initialize(appContext.mockInstance, config)

        // Then
        assertThat(testedFeature.longTaskTrackingStrategy)
            .isInstanceOf(NoOpTrackingStrategy::class.java)
    }

    @Test
    fun `𝕄 store eventMapper 𝕎 initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.rumEventMapper).isSameAs(fakeConfigurationFeature.rumEventMapper)
    }

    @ParameterizedTest
    @EnumSource(VitalsUpdateFrequency::class, names = ["NEVER"], mode = EnumSource.Mode.EXCLUDE)
    fun `𝕄 setup vital monitors 𝕎 initialize { frequency != NEVER }`(
        fakeFrequency: VitalsUpdateFrequency
    ) {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeConfigurationFeature.copy(vitalsMonitorUpdateFrequency = fakeFrequency)
        )

        // Then
        assertThat(testedFeature.cpuVitalMonitor)
            .isInstanceOf(AggregatingVitalMonitor::class.java)
        assertThat(testedFeature.memoryVitalMonitor)
            .isInstanceOf(AggregatingVitalMonitor::class.java)
        assertThat(testedFeature.frameRateVitalMonitor)
            .isInstanceOf(AggregatingVitalMonitor::class.java)
        argumentCaptor<Choreographer.FrameCallback> {
            verify(mockChoreographer).postFrameCallback(capture())
            assertThat(firstValue).isInstanceOf(VitalFrameCallback::class.java)
        }
    }

    @Test
    fun `M not initialize the vital monitors W initialize { frequency = NEVER }`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeConfigurationFeature.copy(
                vitalsMonitorUpdateFrequency = VitalsUpdateFrequency.NEVER
            )
        )

        // Then
        assertThat(testedFeature.cpuVitalMonitor)
            .isInstanceOf(NoOpVitalMonitor::class.java)
        assertThat(testedFeature.memoryVitalMonitor)
            .isInstanceOf(NoOpVitalMonitor::class.java)
        assertThat(testedFeature.frameRateVitalMonitor)
            .isInstanceOf(NoOpVitalMonitor::class.java)
        assertThat(testedFeature.vitalExecutorService)
            .isInstanceOf(NoOpScheduledExecutorService::class.java)
    }

    @ParameterizedTest
    @EnumSource(VitalsUpdateFrequency::class, names = ["NEVER"], mode = EnumSource.Mode.EXCLUDE)
    fun `𝕄 register choreographer callback safely 𝕎 initialize { frequency != NEVER }()`(
        fakeFrequency: VitalsUpdateFrequency,
        @StringForgery message: String
    ) {
        // Given
        doThrow(IllegalStateException(message)).whenever(mockChoreographer).postFrameCallback(any())

        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeConfigurationFeature.copy(vitalsMonitorUpdateFrequency = fakeFrequency)
        )

        // Then
        argumentCaptor<Choreographer.FrameCallback> {
            verify(mockChoreographer).postFrameCallback(capture())
            assertThat(firstValue).isInstanceOf(VitalFrameCallback::class.java)
        }
    }

    @Test
    fun `𝕄 not register choreographer callback 𝕎 initialize { frequency = NEVER }()`(
        @StringForgery message: String
    ) {
        // Given
        doThrow(IllegalStateException(message)).whenever(mockChoreographer).postFrameCallback(any())

        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeConfigurationFeature.copy(
                vitalsMonitorUpdateFrequency = VitalsUpdateFrequency.NEVER
            )
        )

        // Then
        verifyZeroInteractions(mockChoreographer)
    }

    @Test
    fun `𝕄 use noop viewTrackingStrategy 𝕎 stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.viewTrackingStrategy)
            .isInstanceOf(NoOpViewTrackingStrategy::class.java)
    }

    @Test
    fun `𝕄 use noop userActionTrackingStrategy 𝕎 stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.actionTrackingStrategy)
            .isInstanceOf(NoOpUserActionTrackingStrategy::class.java)
    }

    @Test
    fun `𝕄 unregister strategies 𝕎 stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)
        val mockActionTrackingStrategy: UserActionTrackingStrategy = mock()
        val mockViewTrackingStrategy: ViewTrackingStrategy = mock()
        val mockLongTaskTrackingStrategy: TrackingStrategy = mock()
        testedFeature.actionTrackingStrategy = mockActionTrackingStrategy
        testedFeature.viewTrackingStrategy = mockViewTrackingStrategy
        testedFeature.longTaskTrackingStrategy = mockLongTaskTrackingStrategy

        // When
        testedFeature.stop()

        // Then
        verify(mockActionTrackingStrategy).unregister(appContext.mockInstance)
        verify(mockViewTrackingStrategy).unregister(appContext.mockInstance)
        verify(mockLongTaskTrackingStrategy).unregister(appContext.mockInstance)
    }

    @Test
    fun `𝕄 reset eventMapper 𝕎 stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.rumEventMapper).isInstanceOf(NoOpEventMapper::class.java)
    }

    @ParameterizedTest
    @EnumSource(VitalsUpdateFrequency::class, names = ["NEVER"], mode = EnumSource.Mode.EXCLUDE)
    fun `𝕄 initialize vital executor 𝕎 initialize { frequency != NEVER }()`(
        fakeFrequency: VitalsUpdateFrequency
    ) {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeConfigurationFeature.copy(
                vitalsMonitorUpdateFrequency = fakeFrequency
            )
        )

        // Then
        val scheduledRunnables = testedFeature.vitalExecutorService.shutdownNow()
        assertThat(scheduledRunnables).isNotEmpty
    }

    @Test
    fun `𝕄 not initialize vital executor 𝕎 initialize { frequency = NEVER }()`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeConfigurationFeature.copy(
                vitalsMonitorUpdateFrequency = VitalsUpdateFrequency.NEVER
            )
        )

        // Then
        assertThat(testedFeature.vitalExecutorService)
            .isInstanceOf(NoOpScheduledExecutorService::class.java)
    }

    @Test
    fun `𝕄 shut down vital executor 𝕎 stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)
        val mockVitalExecutorService: ScheduledThreadPoolExecutor = mock()
        testedFeature.vitalExecutorService = mockVitalExecutorService

        // When
        testedFeature.stop()

        // Then
        verify(mockVitalExecutorService).shutdownNow()
    }

    @Test
    fun `𝕄 reset vital executor 𝕎 stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.vitalExecutorService)
            .isInstanceOf(NoOpScheduledExecutorService::class.java)
    }

    @Test
    fun `𝕄 reset vital monitors 𝕎 stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.cpuVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
        assertThat(testedFeature.memoryVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
        assertThat(testedFeature.frameRateVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
    }

    @Test
    fun `𝕄 enable RUM debugging 𝕎 enableDebugging()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // When
        testedFeature.enableDebugging()

        // Then
        assertThat(testedFeature.debugActivityLifecycleListener).isNotNull
        verify(testedFeature.appContext as Application)
            .registerActivityLifecycleCallbacks(testedFeature.debugActivityLifecycleListener)
    }

    @Test
    fun `𝕄 disable RUM debugging 𝕎 disableDebugging()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)
        testedFeature.enableDebugging()
        val listener = testedFeature.debugActivityLifecycleListener

        // When
        testedFeature.disableDebugging()

        // Then
        assertThat(testedFeature.debugActivityLifecycleListener).isNull()
        verify(testedFeature.appContext as Application)
            .unregisterActivityLifecycleCallbacks(listener)
    }
}
