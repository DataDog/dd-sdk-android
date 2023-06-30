/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import android.app.Activity
import android.os.Build
import android.view.Display
import android.view.Window
import androidx.metrics.performance.FrameData
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.AdditionalMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
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
    lateinit var mockBuildSdkVersionProvider: BuildSdkVersionProvider

    @Mock
    lateinit var mockActivity: Activity

    @Mock
    lateinit var mockDisplay: Display

    @Mock
    lateinit var mockWindow: Window

    @FloatForgery(30f, 120f)
    var fakeDeviceRefreshRate: Float = 0f

    @BeforeEach
    fun `set up`() {
        whenever(mockBuildSdkVersionProvider.version()) doReturn Build.VERSION_CODES.R
        whenever(mockActivity.window) doReturn mockWindow
        whenever(mockActivity.display) doReturn mockDisplay
        whenever(mockDisplay.refreshRate) doReturn fakeDeviceRefreshRate

        testedJankListener = JankStatsActivityLifecycleListener(
            mockObserver,
            mockBuildSdkVersionProvider,
            mockInternalLogger
        )
        testedJankListener.onActivityCreated(mockActivity, null)
    }

    @Test
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
        @LongForgery(ONE_MILLISSECOND_NS, ONE_SECOND_NS) frameDurationNs: Long,
        @BoolForgery isJank: Boolean
    ) {
        // Given
        val expectedFrameRate = ONE_SECOND_NS.toDouble() / frameDurationNs.toDouble()
        val expectedScale = STANDARD_FPS / fakeDeviceRefreshRate
        val scaledFrameDuration = (frameDurationNs * expectedScale).toLong()
        val frameData = FrameData(timestampNs, scaledFrameDuration, isJank, emptyList())

        // When
        testedJankListener.onFrame(frameData)

        // Then
        verify(mockObserver).onNewSample(eq(expectedFrameRate, 0.0001))
    }

    @Test
    fun `𝕄 do nothing 𝕎 doFrame() {too small duration}`(
        @LongForgery timestampNs: Long,
        @LongForgery(1, ONE_MILLISSECOND_NS) frameDurationNs: Long,
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
        const val STANDARD_FPS: Float = 60f
        const val ONE_MILLISSECOND_NS: Long = 1000L * 1000L
        const val ONE_SECOND_NS: Long = 1000L * 1000L * 1000L
        const val TEN_SECOND_NS: Long = 10L * ONE_SECOND_NS
        const val ONE_MINUTE_NS: Long = 60L * ONE_SECOND_NS
    }
}
