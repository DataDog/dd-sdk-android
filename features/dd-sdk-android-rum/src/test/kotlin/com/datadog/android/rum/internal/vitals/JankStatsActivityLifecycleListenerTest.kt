/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import android.app.Activity
import android.os.Bundle
import android.view.Display
import android.view.Window
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.JankStats
import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.BoolForgery
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
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
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
    lateinit var mockJankStatsProvider: JankStatsProvider

    @Mock
    lateinit var mockJankStats: JankStats

    @BeforeEach
    fun `set up`() {
        whenever(mockActivity.window) doReturn mockWindow
        whenever(mockActivity.display) doReturn mockDisplay
        whenever(mockJankStatsProvider.createJankStatsAndTrack(any(), any(), any())) doReturn mockJankStats

        testedJankListener = JankStatsActivityLifecycleListener(
            mockObserver,
            mockInternalLogger,
            mockJankStatsProvider
        )
    }

    @Test
    fun `𝕄 register jankStats 𝕎 onActivityStarted() {}`() {
        // Given

        // When
        testedJankListener.onActivityStarted(mockActivity)

        // Then
        verify(mockJankStatsProvider).createJankStatsAndTrack(mockWindow, testedJankListener, mockInternalLogger)
    }

    @Test
    fun `𝕄 handle null jankstats 𝕎 onActivityStarted() {}`() {
        // Given
        whenever(mockJankStatsProvider.createJankStatsAndTrack(any(), any(), any())) doReturn null

        // When
        testedJankListener.onActivityStarted(mockActivity)

        // Then
        verify(mockJankStatsProvider).createJankStatsAndTrack(mockWindow, testedJankListener, mockInternalLogger)
    }

    @Test
    fun `𝕄 register jankStats once 𝕎 onActivityStarted() { multiple activities, same window}`() {
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
    fun `𝕄 pause stats 𝕎 onActivityStarted() + onActivityStopped() {}`() {
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
    fun `𝕄 remove window 𝕎 onActivityDestroyed() { no more activities for window }`() {
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
    fun `𝕄 not remove window 𝕎 onActivityDestroyed() { there are activities for window }`() {
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
    fun `𝕄 do nothing 𝕎 onActivityStopped() {}`() {
        // Given

        // When
        testedJankListener.onActivityStopped(mockActivity)

        // Then
        verifyNoInteractions(mockJankStatsProvider, mockJankStats)
    }

    @Test
    fun `𝕄 resume stats 𝕎 onActivityStarted() + onActivityStopped() + onActivityStarted() {}`() {
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
    fun `𝕄 do nothing 𝕎 onActivityCreated() {}`() {
        // Given
        val mockBundle = mock<Bundle>()

        // When
        testedJankListener.onActivityCreated(mockActivity, mockBundle)

        // Then
        verifyNoInteractions(mockJankStatsProvider, mockJankStats, mockBundle)
    }

    @Test
    fun `𝕄 do nothing 𝕎 onActivityResumed() {}`() {
        // When
        testedJankListener.onActivityResumed(mockActivity)

        // Then
        verifyNoInteractions(mockJankStatsProvider, mockJankStats)
    }

    @Test
    fun `𝕄 do nothing 𝕎 onActivityPaused() {}`() {
        // When
        testedJankListener.onActivityPaused(mockActivity)

        // Then
        verifyNoInteractions(mockJankStatsProvider, mockJankStats)
    }

    @Test
    fun `𝕄 do nothing 𝕎 onActivityDestroyed() {}`() {
        // When
        testedJankListener.onActivityDestroyed(mockActivity)

        // Then
        verifyNoInteractions(mockJankStatsProvider, mockJankStats)
    }

    @Test
    fun `𝕄 do nothing 𝕎 onActivitySaveInstanceState() {}`() {
        // Given
        val mockBundle = mock<Bundle>()

        // When
        testedJankListener.onActivitySaveInstanceState(mockActivity, mockBundle)

        // Then
        verifyNoInteractions(mockJankStatsProvider, mockJankStats, mockBundle)
    }

    fun `𝕄 do nothing 𝕎 onFrame() {zero ns duration}`(
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
    fun `𝕄 forward frame rate to observer 𝕎 doFrame() {acceptable frame rate}`(
        @LongForgery timestampNs: Long,
        @LongForgery(ONE_MILLISECOND_NS, ONE_SECOND_NS) frameDurationNs: Long,
        @BoolForgery isJank: Boolean
    ) {
        // Given
        val expectedFrameRate = ONE_SECOND_NS.toDouble() / frameDurationNs.toDouble()
        val frameData = FrameData(timestampNs, frameDurationNs, isJank, emptyList())

        // When
        testedJankListener.onFrame(frameData)

        // Then
        verify(mockObserver).onNewSample(eq(expectedFrameRate, 0.0001))
    }

    @Test
    fun `𝕄 do nothing 𝕎 doFrame() {too small duration}`(
        @LongForgery timestampNs: Long,
        @LongForgery(1, ONE_MILLISECOND_NS) frameDurationNs: Long,
        @BoolForgery isJank: Boolean
    ) {
        // Given
        val frameData = FrameData(timestampNs, frameDurationNs, isJank, emptyList())

        // When
        testedJankListener.onFrame(frameData)

        // Then
        verify(mockObserver, never()).onNewSample(any())
    }

    @Test
    fun `𝕄 do nothing 𝕎 doFrame() {too large duration}`(
        @LongForgery timestampNs: Long,
        @LongForgery(TEN_SECOND_NS, ONE_MINUTE_NS) frameDurationNs: Long,
        @BoolForgery isJank: Boolean
    ) {
        // Given
        val frameData = FrameData(timestampNs, frameDurationNs, isJank, emptyList())

        // When
        testedJankListener.onFrame(frameData)

        // Then
        verify(mockObserver, never()).onNewSample(any())
    }

    companion object {
        const val ONE_MILLISECOND_NS: Long = 1000L * 1000L
        const val ONE_SECOND_NS: Long = 1000L * 1000L * 1000L
        const val TEN_SECOND_NS: Long = 10L * ONE_SECOND_NS
        const val ONE_MINUTE_NS: Long = 60L * ONE_SECOND_NS
    }
}
