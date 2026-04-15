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
import com.datadog.android.rum.startup.RumFirstDrawTimeReporter
import com.datadog.android.rum.internal.startup.RumStartupScenario
import com.datadog.android.rum.internal.startup.RumTTIDInfo
import com.datadog.android.rum.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.rum.utils.config.MainLooperTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.getFieldValue
import com.datadog.tools.unit.setFieldValue
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
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
    fun `M send TTID with wasForwarded=false W onAppStartupDetected + first frame drawn`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)

        val listener = extractStartupDetectorListener()
        val mockFirstDrawReporter = replaceFirstDrawReporterWithMock(listener)
        val mockDetector = replaceDetectorWithMock()

        val fakeActivity = mock<Activity>()
        val fakeTime = Time(nanoTime = 100_000L)
        val fakeScenario = RumStartupScenario.Cold(
            hasSavedInstanceStateBundle = false,
            activity = WeakReference(fakeActivity),
            appStartActivityOnCreateGapNs = 0L,
            initialTime = fakeTime
        )

        whenever(mockDetector.getPendingScenario()) doReturn fakeScenario

        val callbackCaptor = argumentCaptor<RumFirstDrawTimeReporter.Callback>()

        // When
        listener.onAppStartupDetected(fakeScenario)

        // Then — sendAppStartEvent should be called
        verify(mockRumMonitor).sendAppStartEvent(fakeScenario)

        verify(mockFirstDrawReporter).subscribeToFirstFrameDrawn(
            eq(fakeActivity),
            callbackCaptor.capture()
        )

        // When — simulate first frame drawn
        val fakeTimestampNs = 200_000L
        callbackCaptor.firstValue.onFirstFrameDrawn(fakeTimestampNs)

        // Then
        val ttidInfoCaptor = argumentCaptor<RumTTIDInfo>()
        verify(mockRumMonitor).sendTTIDEvent(ttidInfoCaptor.capture())

        val ttidInfo = ttidInfoCaptor.firstValue
        assertThat(ttidInfo.scenario).isSameAs(fakeScenario)
        assertThat(ttidInfo.durationNs).isEqualTo(fakeTimestampNs - fakeTime.nanoTime)
        assertThat(ttidInfo.wasForwarded).isFalse()
        verify(mockDetector).clearPendingScenario()
    }

    @Test
    fun `M do nothing W onAppStartupDetected + activity GCd`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)

        val listener = extractStartupDetectorListener()
        val mockFirstDrawReporter = replaceFirstDrawReporterWithMock(listener)

        val fakeTime = Time(nanoTime = 100_000L)
        val fakeScenario = RumStartupScenario.Cold(
            hasSavedInstanceStateBundle = false,
            activity = WeakReference(null), // GC'd — get() returns null
            appStartActivityOnCreateGapNs = 0L,
            initialTime = fakeTime
        )

        // When
        listener.onAppStartupDetected(fakeScenario)

        // Then
        verify(mockRumMonitor, never()).sendAppStartEvent(any())
        verify(mockFirstDrawReporter, never()).subscribeToFirstFrameDrawn(any(), any())
    }

    @Test
    fun `M do nothing W onAppStartupDetected + monitor not AdvancedRumMonitor`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)

        val listener = extractStartupDetectorListener()
        val mockFirstDrawReporter = replaceFirstDrawReporterWithMock(listener)

        // Replace the registered AdvancedRumMonitor with a plain RumMonitor
        // so the cast in onAppStartupDetected returns null
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
        verify(mockFirstDrawReporter, never()).subscribeToFirstFrameDrawn(any(), any())
    }

    @Test
    fun `M call sendAppStartEvent only in onAppStartupDetected W startup flow`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)

        val listener = extractStartupDetectorListener()
        replaceFirstDrawReporterWithMock(listener)
        replaceDetectorWithMock()

        val fakeOriginalActivity = mock<Activity>()
        val fakeForwardedActivity = mock<Activity>()
        val fakeTime = Time(nanoTime = 100_000L)
        val fakeScenario = RumStartupScenario.Cold(
            hasSavedInstanceStateBundle = false,
            activity = WeakReference(fakeOriginalActivity),
            appStartActivityOnCreateGapNs = 0L,
            initialTime = fakeTime
        )

        // When
        listener.onAppStartupDetected(fakeScenario)

        // Then — sendAppStartEvent called once
        verify(mockRumMonitor, times(1)).sendAppStartEvent(fakeScenario)

        // When
        listener.onNextActivityCreated(fakeScenario, fakeForwardedActivity)

        // Then — still only called once (not from onNextActivityCreated)
        verify(mockRumMonitor, times(1)).sendAppStartEvent(any())
    }

    // endregion

    // region onNextActivityCreated

    @Test
    fun `M send TTID with wasForwarded=true W onNextActivityCreated + first frame drawn`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)

        val listener = extractStartupDetectorListener()
        val mockFirstDrawReporter = replaceFirstDrawReporterWithMock(listener)
        val mockDetector = replaceDetectorWithMock()

        val fakeOriginalActivity = mock<Activity>()
        val fakeForwardedActivity = mock<Activity>()
        val fakeTime = Time(nanoTime = 100_000L)
        val fakeScenario = RumStartupScenario.Cold(
            hasSavedInstanceStateBundle = false,
            activity = WeakReference(fakeOriginalActivity),
            appStartActivityOnCreateGapNs = 0L,
            initialTime = fakeTime
        )

        whenever(mockDetector.getPendingScenario()) doReturn fakeScenario

        val callbackCaptor = argumentCaptor<RumFirstDrawTimeReporter.Callback>()

        // When
        listener.onNextActivityCreated(fakeScenario, fakeForwardedActivity)

        // Then — sendAppStartEvent should NOT be called via onNextActivityCreated
        verify(mockRumMonitor, times(0)).sendAppStartEvent(any())

        verify(mockFirstDrawReporter).subscribeToFirstFrameDrawn(
            eq(fakeForwardedActivity),
            callbackCaptor.capture()
        )

        // When — simulate first frame drawn
        val fakeTimestampNs = 200_000L
        callbackCaptor.firstValue.onFirstFrameDrawn(fakeTimestampNs)

        // Then
        val ttidInfoCaptor = argumentCaptor<RumTTIDInfo>()
        verify(mockRumMonitor).sendTTIDEvent(ttidInfoCaptor.capture())

        val ttidInfo = ttidInfoCaptor.firstValue
        assertThat(ttidInfo.scenario).isSameAs(fakeScenario)
        assertThat(ttidInfo.durationNs).isEqualTo(fakeTimestampNs - fakeTime.nanoTime)
        assertThat(ttidInfo.wasForwarded).isTrue()
        verify(mockDetector).clearPendingScenario()
    }

    @Test
    fun `M do nothing W onNextActivityCreated + monitor not AdvancedRumMonitor`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)

        val listener = extractStartupDetectorListener()
        val mockFirstDrawReporter = replaceFirstDrawReporterWithMock(listener)

        // Replace the registered AdvancedRumMonitor with a plain RumMonitor
        // so the cast in onNextActivityCreated returns null
        GlobalRumMonitor.clear()
        GlobalRumMonitor.registerIfAbsent(mock<RumMonitor>(), mockSdkCore)

        val fakeForwardedActivity = mock<Activity>()
        val fakeTime = Time(nanoTime = 100_000L)
        val fakeScenario = RumStartupScenario.Cold(
            hasSavedInstanceStateBundle = false,
            activity = WeakReference(mock()),
            appStartActivityOnCreateGapNs = 0L,
            initialTime = fakeTime
        )

        // When
        listener.onNextActivityCreated(fakeScenario, fakeForwardedActivity)

        // Then
        verify(mockFirstDrawReporter, never()).subscribeToFirstFrameDrawn(any(), any())
    }

    // endregion

    // region duplicate prevention

    @Test
    fun `M only send TTID once W both original and forwarded activity draw`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)

        val listener = extractStartupDetectorListener()
        val mockFirstDrawReporter = replaceFirstDrawReporterWithMock(listener)
        val mockDetector = replaceDetectorWithMock()

        val fakeOriginalActivity = mock<Activity>()
        val fakeForwardedActivity = mock<Activity>()
        val fakeTime = Time(nanoTime = 100_000L)
        val fakeScenario = RumStartupScenario.Cold(
            hasSavedInstanceStateBundle = false,
            activity = WeakReference(fakeOriginalActivity),
            appStartActivityOnCreateGapNs = 0L,
            initialTime = fakeTime
        )

        whenever(mockDetector.getPendingScenario()) doReturn fakeScenario

        val callbackCaptor = argumentCaptor<RumFirstDrawTimeReporter.Callback>()

        // When — trigger both callbacks
        listener.onAppStartupDetected(fakeScenario)
        listener.onNextActivityCreated(fakeScenario, fakeForwardedActivity)

        verify(mockFirstDrawReporter, times(2)).subscribeToFirstFrameDrawn(
            any(),
            callbackCaptor.capture()
        )

        val originalCallback = callbackCaptor.allValues[0]
        val forwardedCallback = callbackCaptor.allValues[1]

        // First draw fires (original activity)
        originalCallback.onFirstFrameDrawn(200_000L)

        // After first TTID is sent, scenario is cleared
        verify(mockDetector).clearPendingScenario()
        whenever(mockDetector.getPendingScenario()) doReturn null

        // Second draw fires (forwarded activity) — should be no-op
        forwardedCallback.onFirstFrameDrawn(300_000L)

        // Then — sendTTIDEvent should only be called once
        verify(mockRumMonitor, times(1)).sendTTIDEvent(any())
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

    /**
     * Replaces the `rumFirstDrawTimeReporter` field inside the anonymous listener with a mock,
     * so we can capture the [RumFirstDrawTimeReporter.Callback] passed to
     * [RumFirstDrawTimeReporter.subscribeToFirstFrameDrawn].
     */
    private fun replaceFirstDrawReporterWithMock(
        listener: RumAppStartupDetector.Listener
    ): RumFirstDrawTimeReporter {
        val mockReporter = mock<RumFirstDrawTimeReporter>()
        listener.setFieldValue("rumFirstDrawTimeReporter", mockReporter)
        return mockReporter
    }

    /**
     * Replaces the `rumAppStartupDetector` field on [RumFeature] with a mock so
     * [RumAppStartupDetector.getPendingScenario] and [RumAppStartupDetector.clearPendingScenario]
     * can be controlled.
     */
    private fun replaceDetectorWithMock(): RumAppStartupDetector {
        val mockDetector = mock<RumAppStartupDetector>()
        testedFeature.setFieldValue("rumAppStartupDetector", mockDetector)
        return mockDetector
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
