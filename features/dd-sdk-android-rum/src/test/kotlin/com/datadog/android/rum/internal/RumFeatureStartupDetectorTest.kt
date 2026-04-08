/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import android.app.Activity
import android.app.Application
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.startup.RumAppStartupDetector
import com.datadog.android.rum.internal.startup.RumAppStartupDetectorImpl
import com.datadog.android.rum.internal.startup.RumStartupScenario
import com.datadog.android.rum.internal.startup.RumTTIDInfo
import com.datadog.android.rum.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.rum.utils.config.MainLooperTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.getFieldValue
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
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.lang.ref.WeakReference
import java.util.UUID

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumFeatureStartupDetectorTest {

    private lateinit var testedFeature: RumFeature

    @Forgery
    lateinit var fakeApplicationId: UUID

    @Forgery
    lateinit var fakeConfiguration: RumFeature.Configuration

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockRumMonitor: AdvancedRumMonitor

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockLateCrashReporter: LateCrashReporter

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.timeProvider) doReturn mock()
        whenever(mockSdkCore.createScheduledExecutorService(any())) doReturn mock()

        val mockContentResolver = mock<android.content.ContentResolver>()
        whenever(appContext.mockInstance.contentResolver) doReturn mockContentResolver
        doNothing().whenever(appContext.mockInstance).registerComponentCallbacks(any())
        doNothing().whenever(appContext.mockInstance).unregisterComponentCallbacks(any())

        val mockResources = mock<android.content.res.Resources>()
        whenever(appContext.mockInstance.resources) doReturn mockResources
        whenever(mockResources.configuration) doReturn mock()

        testedFeature = RumFeature(
            mockSdkCore,
            fakeApplicationId.toString(),
            fakeConfiguration,
            lateCrashReporterFactory = { mockLateCrashReporter }
        )
        GlobalRumMonitor.registerIfAbsent(mockRumMonitor, mockSdkCore)
    }

    @AfterEach
    fun `tear down`() {
        GlobalRumMonitor.clear()
    }

    // region onAppStartupDetected

    @Test
    fun `M send app start event W onAppStartupDetected`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)

        val listener = extractStartupDetectorListener()

        val fakeActivity = mock<Activity>()
        val fakeTime = Time(nanoTime = 100_000L)
        val fakeScenario = RumStartupScenario.Cold(
            hasSavedInstanceStateBundle = false,
            activity = WeakReference(fakeActivity),
            appStartActivityOnCreateGapNs = 0L,
            initialTime = fakeTime
        )

        // When
        listener.onAppStartupDetected(fakeScenario)

        // Then
        verify(mockRumMonitor).sendAppStartEvent(fakeScenario)
    }

    @Test
    fun `M do nothing W onAppStartupDetected + monitor not AdvancedRumMonitor`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)

        val listener = extractStartupDetectorListener()

        GlobalRumMonitor.clear()
        GlobalRumMonitor.registerIfAbsent(mock<RumMonitor>(), mockSdkCore)

        val fakeActivity = mock<Activity>()
        val fakeTime = Time(nanoTime = 100_000L)
        val fakeScenario = RumStartupScenario.Cold(
            hasSavedInstanceStateBundle = false,
            activity = WeakReference(fakeActivity),
            appStartActivityOnCreateGapNs = 0L,
            initialTime = fakeTime
        )

        // When
        listener.onAppStartupDetected(fakeScenario)

        // Then
        verify(mockRumMonitor, never()).sendAppStartEvent(any())
    }

    // endregion

    // region onTTIDComputed

    @Test
    fun `M send TTID event W onTTIDComputed`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)

        val listener = extractStartupDetectorListener()

        val fakeActivity = mock<Activity>()
        val fakeTime = Time(nanoTime = 100_000L)
        val fakeScenario = RumStartupScenario.Cold(
            hasSavedInstanceStateBundle = false,
            activity = WeakReference(fakeActivity),
            appStartActivityOnCreateGapNs = 0L,
            initialTime = fakeTime
        )
        val fakeDurationNs = 100_000L

        // When
        listener.onTTIDComputed(fakeScenario, fakeDurationNs, wasForwarded = false)

        // Then
        val ttidInfoCaptor = argumentCaptor<RumTTIDInfo>()
        verify(mockRumMonitor).sendTTIDEvent(ttidInfoCaptor.capture())

        val ttidInfo = ttidInfoCaptor.firstValue
        assertThat(ttidInfo.scenario).isSameAs(fakeScenario)
        assertThat(ttidInfo.durationNs).isEqualTo(fakeDurationNs)
        assertThat(ttidInfo.wasForwarded).isFalse()
    }

    @Test
    fun `M send TTID event with wasForwarded=true W onTTIDComputed`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)

        val listener = extractStartupDetectorListener()

        val fakeActivity = mock<Activity>()
        val fakeTime = Time(nanoTime = 100_000L)
        val fakeScenario = RumStartupScenario.Cold(
            hasSavedInstanceStateBundle = false,
            activity = WeakReference(fakeActivity),
            appStartActivityOnCreateGapNs = 0L,
            initialTime = fakeTime
        )
        val fakeDurationNs = 100_000L

        // When
        listener.onTTIDComputed(fakeScenario, fakeDurationNs, wasForwarded = true)

        // Then
        val ttidInfoCaptor = argumentCaptor<RumTTIDInfo>()
        verify(mockRumMonitor).sendTTIDEvent(ttidInfoCaptor.capture())

        val ttidInfo = ttidInfoCaptor.firstValue
        assertThat(ttidInfo.scenario).isSameAs(fakeScenario)
        assertThat(ttidInfo.durationNs).isEqualTo(fakeDurationNs)
        assertThat(ttidInfo.wasForwarded).isTrue()
    }

    @Test
    fun `M do nothing W onTTIDComputed + monitor not AdvancedRumMonitor`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)

        val listener = extractStartupDetectorListener()

        GlobalRumMonitor.clear()
        GlobalRumMonitor.registerIfAbsent(mock<RumMonitor>(), mockSdkCore)

        val fakeScenario = RumStartupScenario.Cold(
            hasSavedInstanceStateBundle = false,
            activity = WeakReference(mock()),
            appStartActivityOnCreateGapNs = 0L,
            initialTime = Time(nanoTime = 100_000L)
        )

        // When
        listener.onTTIDComputed(fakeScenario, 100_000L, wasForwarded = false)

        // Then
        verify(mockRumMonitor, never()).sendTTIDEvent(any())
    }

    // endregion

    // region helpers

    /**
     * Extracts the [RumAppStartupDetector.Listener] from the real detector created during
     * [RumFeature.onInitialize].
     */
    private fun extractStartupDetectorListener(): RumAppStartupDetector.Listener {
        val detector = testedFeature.getFieldValue<RumAppStartupDetector, RumFeature>(
            "rumAppStartupDetector",
            RumFeature::class.java
        )
        val detectorImpl = detector as RumAppStartupDetectorImpl
        return detectorImpl.getFieldValue<RumAppStartupDetector.Listener, RumAppStartupDetectorImpl>(
            "listener",
            RumAppStartupDetectorImpl::class.java
        )
    }

    // endregion

    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)
        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext, mainLooper)
        }
    }
}
