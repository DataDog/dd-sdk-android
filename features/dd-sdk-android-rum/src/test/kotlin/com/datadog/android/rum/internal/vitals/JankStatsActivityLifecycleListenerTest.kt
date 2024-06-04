/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.View
import android.view.Window
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.JankStats
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import com.datadog.android.rum.utils.config.MainLooperTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.rum.utils.verifyLog
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.AdditionalMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import kotlin.math.min

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class JankStatsActivityLifecycleListenerTest {

    lateinit var testedJankListener: JankStatsActivityLifecycleListener

    @Mock
    lateinit var mockObserver: VitalObserver

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockActivity: Activity

    @Mock
    lateinit var mockDisplay: Display

    @Mock
    lateinit var mockWindow: Window

    @Mock
    lateinit var mockDecorView: View

    @Mock
    lateinit var mockJankStatsProvider: JankStatsProvider

    @Mock
    lateinit var mockJankStats: JankStats

    @BeforeEach
    fun `set up`() {
        whenever(mockWindow.decorView) doReturn mockDecorView
        whenever(mockWindow.peekDecorView()) doReturn mockDecorView
        whenever(mockActivity.window) doReturn mockWindow
        whenever(mockActivity.display) doReturn mockDisplay
        whenever(mockJankStatsProvider.createJankStatsAndTrack(any(), any(), any())) doReturn mockJankStats
        whenever(mockJankStats.isTrackingEnabled) doReturn true

        testedJankListener = JankStatsActivityLifecycleListener(
            mockObserver,
            mockInternalLogger,
            mockJankStatsProvider
        )
    }

    @Test
    fun `M register jankStats W onActivityStarted() {}`() {
        // Given

        // When
        testedJankListener.onActivityStarted(mockActivity)

        // Then
        verify(mockJankStatsProvider).createJankStatsAndTrack(mockWindow, testedJankListener, mockInternalLogger)
    }

    @Test
    fun `M handle null jankStats W onActivityStarted() {}`() {
        // Given
        whenever(mockJankStatsProvider.createJankStatsAndTrack(any(), any(), any())) doReturn null

        // When
        testedJankListener.onActivityStarted(mockActivity)

        // Then
        verify(mockJankStatsProvider).createJankStatsAndTrack(mockWindow, testedJankListener, mockInternalLogger)
    }

    @Test
    fun `M register jankStats once W onActivityStarted() { multiple activities, same window}`() {
        // Given
        val mockActivity2 = mock<Activity>()
        whenever(mockActivity2.window) doReturn mockWindow
        whenever(mockActivity2.display) doReturn mockDisplay
        testedJankListener = JankStatsActivityLifecycleListener(
            mockObserver,
            mockInternalLogger,
            mockJankStatsProvider
        )
        reset(mockJankStatsProvider)
        whenever(mockJankStatsProvider.createJankStatsAndTrack(any(), any(), any())) doReturn mockJankStats

        // When
        testedJankListener.onActivityStarted(mockActivity)
        testedJankListener.onActivityStarted(mockActivity2)

        // Then
        verify(mockJankStatsProvider).createJankStatsAndTrack(mockWindow, testedJankListener, mockInternalLogger)
    }

    @Test
    fun `M pause stats W onActivityStarted() + onActivityStopped() {}`() {
        // Given

        // When
        testedJankListener.onActivityStarted(mockActivity)
        testedJankListener.onActivityStopped(mockActivity)

        // Then
        inOrder(mockJankStatsProvider, mockJankStats) {
            verify(mockJankStatsProvider).createJankStatsAndTrack(mockWindow, testedJankListener, mockInternalLogger)
            verify(mockJankStats).isTrackingEnabled = false
        }
    }

    @Test
    fun `M log error W onActivityStopped() { jankStats instance is already stopped }`() {
        // Given
        whenever(mockJankStats.isTrackingEnabled) doReturn false

        // When
        testedJankListener.onActivityStarted(mockActivity)
        testedJankListener.onActivityStopped(mockActivity)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.TELEMETRY,
            JankStatsActivityLifecycleListener.JANK_STATS_TRACKING_ALREADY_DISABLED_ERROR
        )
    }

    @Test
    fun `M log error W onActivityStopped() { jankStats stop tracking throws IAE error }`() {
        // Given
        val exception = IllegalArgumentException()
        whenever(mockJankStats::isTrackingEnabled.set(false)) doThrow exception

        // When
        testedJankListener.onActivityStarted(mockActivity)
        testedJankListener.onActivityStopped(mockActivity)

        // Then
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.TELEMETRY,
            message = JankStatsActivityLifecycleListener.JANK_STATS_TRACKING_DISABLE_ERROR,
            throwable = exception
        )
    }

    @Test
    fun `M log error W onActivityStopped() { jankStats stop tracking throws NPE error }`() {
        // Given
        val exception = NullPointerException()
        whenever(mockJankStats::isTrackingEnabled.set(false)) doThrow exception

        // When
        testedJankListener.onActivityStarted(mockActivity)
        testedJankListener.onActivityStopped(mockActivity)

        // Then
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.TELEMETRY,
            message = JankStatsActivityLifecycleListener.JANK_STATS_TRACKING_DISABLE_ERROR,
            throwable = exception
        )
    }

    @Test
    fun `M remove window W onActivityDestroyed() { no more activities for window }`() {
        // Given

        // When
        testedJankListener.onActivityStarted(mockActivity)
        testedJankListener.onActivityStopped(mockActivity)
        testedJankListener.onActivityDestroyed(mockActivity)

        // Then
        assertThat(testedJankListener.activeActivities).isEmpty()
        assertThat(testedJankListener.activeWindowsListener).isEmpty()
    }

    @Test
    fun `M not remove window W onActivityDestroyed() { there are activities for window }`() {
        // Given
        val anotherActivity = mock<Activity>().apply {
            whenever(window) doReturn mockWindow
            whenever(display) doReturn mockDisplay
        }

        // When
        testedJankListener.onActivityStarted(mockActivity)
        testedJankListener.onActivityStopped(mockActivity)
        testedJankListener.onActivityStarted(anotherActivity)
        testedJankListener.onActivityDestroyed(mockActivity)

        // Then
        assertThat(testedJankListener.activeActivities).isNotEmpty
        assertThat(testedJankListener.activeWindowsListener).isNotEmpty
    }

    @Test
    fun `M do nothing W onActivityStopped() {}`() {
        // Given

        // When
        testedJankListener.onActivityStopped(mockActivity)

        // Then
        verifyNoInteractions(mockJankStatsProvider, mockJankStats)
    }

    @Test
    fun `M resume stats W onActivityStarted() + onActivityStopped() + onActivityStarted() {}`() {
        // Given

        // When
        testedJankListener.onActivityStarted(mockActivity)
        testedJankListener.onActivityStopped(mockActivity)
        testedJankListener.onActivityStarted(mockActivity)

        // Then
        inOrder(mockJankStatsProvider, mockJankStats) {
            verify(mockJankStatsProvider).createJankStatsAndTrack(mockWindow, testedJankListener, mockInternalLogger)
            verify(mockJankStats).isTrackingEnabled = false
            verify(mockJankStats).isTrackingEnabled = true
        }
    }

    @Test
    fun `M add listener to window only once W onActivityStarted()`() {
        // Given
        whenever(mockDecorView.isHardwareAccelerated) doReturn true
        val mockBuildSdkVersionProvider: BuildSdkVersionProvider = mock()
        whenever(mockBuildSdkVersionProvider.version) doReturn Build.VERSION_CODES.S
        testedJankListener = JankStatsActivityLifecycleListener(
            mockObserver,
            mockInternalLogger,
            mockJankStatsProvider,
            60.0,
            mockBuildSdkVersionProvider
        )

        // When
        testedJankListener.onActivityStarted(mockActivity)
        testedJankListener.onActivityStopped(mockActivity)
        testedJankListener.onActivityStarted(mockActivity)

        // Then
        verify(mockWindow).addOnFrameMetricsAvailableListener(any(), any()) // should be called only once
    }

    @Test
    fun `M do nothing W onActivityCreated() {}`() {
        // Given
        val mockBundle = mock<Bundle>()

        // When
        testedJankListener.onActivityCreated(mockActivity, mockBundle)

        // Then
        verifyNoInteractions(mockJankStatsProvider, mockJankStats, mockBundle)
    }

    @Test
    fun `M do nothing W onActivityResumed() {}`() {
        // When
        testedJankListener.onActivityResumed(mockActivity)

        // Then
        verifyNoInteractions(mockJankStatsProvider, mockJankStats)
    }

    @Test
    fun `M do nothing W onActivityPaused() {}`() {
        // When
        testedJankListener.onActivityPaused(mockActivity)

        // Then
        verifyNoInteractions(mockJankStatsProvider, mockJankStats)
    }

    @Test
    fun `M do nothing W onActivityDestroyed() {}`() {
        // When
        testedJankListener.onActivityDestroyed(mockActivity)

        // Then
        verifyNoInteractions(mockJankStatsProvider, mockJankStats)
    }

    @Test
    fun `M do nothing W onActivitySaveInstanceState() {}`() {
        // Given
        val mockBundle = mock<Bundle>()

        // When
        testedJankListener.onActivitySaveInstanceState(mockActivity, mockBundle)

        // Then
        verifyNoInteractions(mockJankStatsProvider, mockJankStats, mockBundle)
    }

    fun `M do nothing W onFrame() {zero ns duration}`(
        @LongForgery timestampNs: Long,
        @BoolForgery isJank: Boolean
    ) {
        // Given
        val frameData = FrameData(timestampNs, 0L, isJank, emptyList())

        // When
        testedJankListener.onFrame(frameData)

        // Then
        verify(mockObserver, never()).onNewSample(any())
    }

    @Test
    fun `M forward frame rate to observer W doFrame() {acceptable frame rate}`(
        @LongForgery timestampNs: Long,
        @LongForgery(ONE_MILLISECOND_NS, ONE_SECOND_NS) frameDurationNs: Long,
        @BoolForgery isJank: Boolean
    ) {
        // Given
        val expectedFrameRate = (ONE_SECOND_NS.toDouble() / frameDurationNs.toDouble()).coerceAtMost(MAX_FPS)
        val frameData = FrameData(timestampNs, frameDurationNs, isJank, emptyList())

        // When
        testedJankListener.onFrame(frameData)

        // Then
        verify(mockObserver).onNewSample(eq(expectedFrameRate, 0.0001))
    }

    @Test
    fun `M adjust sample value to refresh rate W doFrame() {S, refresh rate over 60hz}`(
        @LongForgery timestampNs: Long,
        @LongForgery(ONE_MILLISECOND_NS, ONE_SECOND_NS) frameDurationNs: Long,
        @BoolForgery isJank: Boolean,
        @DoubleForgery(60.0, 120.0) displayRefreshRate: Double
    ) {
        // Given
        val expectedFrameRate = ONE_SECOND_NS.toDouble() / frameDurationNs.toDouble()
        val refreshRateMultiplier = 60.0 / displayRefreshRate

        val frameData = FrameData(timestampNs, frameDurationNs, isJank, emptyList())

        val mockBuildSdkVersionProvider: BuildSdkVersionProvider = mock()
        whenever(mockBuildSdkVersionProvider.version) doReturn Build.VERSION_CODES.S

        val variableRefreshRateListener = JankStatsActivityLifecycleListener(
            mockObserver,
            mockInternalLogger,
            mockJankStatsProvider,
            displayRefreshRate,
            mockBuildSdkVersionProvider
        )
        variableRefreshRateListener.frameDeadline = (ONE_SECOND_NS / displayRefreshRate).toLong()

        // When
        variableRefreshRateListener.onFrame(frameData)

        // Then
        if (expectedFrameRate * refreshRateMultiplier > MIN_FPS) {
            verify(mockObserver).onNewSample(eq(min(expectedFrameRate * refreshRateMultiplier, MAX_FPS), 0.0001))
        } else {
            verify(mockObserver, never()).onNewSample(any())
        }
    }

    @Test
    fun `M adjust sample value to refresh rate W doFrame() {R, refresh rate over 60hz}`(
        @LongForgery timestampNs: Long,
        @LongForgery(ONE_MILLISECOND_NS, ONE_SECOND_NS) frameDurationNs: Long,
        @BoolForgery isJank: Boolean,
        @DoubleForgery(60.0, 120.0) displayRefreshRate: Double
    ) {
        // Given
        val expectedFrameRate = ONE_SECOND_NS.toDouble() / frameDurationNs.toDouble()
        val refreshRateMultiplier = 60.0 / displayRefreshRate

        val frameData = FrameData(timestampNs, frameDurationNs, isJank, emptyList())

        val mockBuildSdkVersionProvider: BuildSdkVersionProvider = mock()
        whenever(mockBuildSdkVersionProvider.version) doReturn Build.VERSION_CODES.R

        val mockDisplay: Display = mock()
        whenever(mockDisplay.refreshRate) doReturn displayRefreshRate.toFloat()

        val variableRefreshRateListener = JankStatsActivityLifecycleListener(
            mockObserver,
            mockInternalLogger,
            mockJankStatsProvider,
            displayRefreshRate,
            mockBuildSdkVersionProvider
        )

        variableRefreshRateListener.display = mockDisplay

        // When
        variableRefreshRateListener.onFrame(frameData)

        // Then
        if (expectedFrameRate * refreshRateMultiplier > MIN_FPS) {
            verify(mockObserver).onNewSample(eq(min(expectedFrameRate * refreshRateMultiplier, MAX_FPS), 0.0001))
        } else {
            verify(mockObserver, never()).onNewSample(any())
        }
    }

    @Test
    fun `M do nothing W onActivityStarted() {android framework throws an exception}`() {
        // Given
        whenever(mockWindow.addOnFrameMetricsAvailableListener(any(), any())) doThrow IllegalStateException()

        // When
        testedJankListener.onActivityStarted(mockActivity)

        // Then
        // no-crash
    }

    @Test
    fun `M do nothing W onActivityStarted() + onActivityDestroyed() {android framework throws an exception}`() {
        // Given
        whenever(mockWindow.removeOnFrameMetricsAvailableListener(any())) doThrow IllegalArgumentException()

        // When
        testedJankListener.onActivityStarted(mockActivity)
        testedJankListener.onActivityDestroyed(mockActivity)

        // Then
        // no-crash
    }

    companion object {
        const val ONE_MILLISECOND_NS: Long = 1000L * 1000L
        const val ONE_SECOND_NS: Long = 1000L * 1000L * 1000L
        const val TEN_SECOND_NS: Long = 10L * ONE_SECOND_NS
        const val ONE_MINUTE_NS: Long = 60L * ONE_SECOND_NS
        const val MIN_FPS: Double = 1.0
        const val MAX_FPS: Double = 60.0

        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(mainLooper)
        }
    }
}
