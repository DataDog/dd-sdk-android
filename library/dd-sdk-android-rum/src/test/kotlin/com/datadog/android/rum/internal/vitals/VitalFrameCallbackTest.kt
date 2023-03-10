/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import android.app.Application
import android.content.Context
import android.view.Choreographer
import android.view.Display
import android.view.WindowManager
import com.datadog.android.rum.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.rum.utils.config.InternalLoggerTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.utils.extension.mockChoreographerInstance
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.offset
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
internal class VitalFrameCallbackTest {

    lateinit var testedFrameCallback: VitalFrameCallback

    @Mock
    lateinit var mockObserver: VitalObserver

    @Mock
    lateinit var mockChoreographer: Choreographer

    @BeforeEach
    fun `set up`() {
        testedFrameCallback = VitalFrameCallback(appContext.mockInstance, mockObserver) { true }

        mockChoreographerInstance(mockChoreographer)
    }

    @Test
    fun `𝕄 do nothing 𝕎 doFrame() {single frame timestamp}`(
        @LongForgery timestampNs: Long
    ) {
        // Given

        // When
        testedFrameCallback.doFrame(timestampNs)

        // Then
        verify(mockObserver, never()).onNewSample(any())
    }

    @Test
    fun `𝕄 do nothing 𝕎 doFrame() {negative duration}`(
        @LongForgery timestampNs: Long,
        @LongForgery(1, ONE_MILLISECOND_NS) frameDurationNs: Long
    ) {
        // Given
        testedFrameCallback.lastFrameTimestampNs = timestampNs + frameDurationNs

        // When
        testedFrameCallback.doFrame(timestampNs)

        // Then
        verify(mockObserver, never()).onNewSample(any())
    }

    @Test
    fun `𝕄 do nothing 𝕎 doFrame() {too small duration}`(
        @LongForgery timestampNs: Long,
        @LongForgery(1, ONE_MILLISECOND_NS) frameDurationNs: Long
    ) {
        // Given

        // When
        testedFrameCallback.doFrame(timestampNs)
        testedFrameCallback.doFrame(timestampNs + frameDurationNs)

        // Then
        verify(mockObserver, never()).onNewSample(any())
    }

    @Test
    fun `𝕄 do nothing 𝕎 doFrame() {too large duration}`(
        @LongForgery timestampNs: Long,
        @LongForgery(TEN_SECOND_NS, ONE_MINUTE_NS) frameDurationNs: Long
    ) {
        // Given

        // When
        testedFrameCallback.doFrame(timestampNs)
        testedFrameCallback.doFrame(timestampNs + frameDurationNs)

        // Then
        verify(mockObserver, never()).onNewSample(any())
    }

    @Test
    fun `𝕄 forward frame rate to observer 𝕎 doFrame() {two frame timestamp}`(
        @LongForgery timestampNs: Long,
        @LongForgery(ONE_MILLISECOND_NS, ONE_SECOND_NS) frameDurationNs: Long
    ) {
        // Given
        val expectedFrameRate = ONE_SECOND_NS.toDouble() / frameDurationNs.toDouble()

        // When
        testedFrameCallback.doFrame(timestampNs)
        testedFrameCallback.doFrame(timestampNs + frameDurationNs)

        // Then
        verify(mockObserver).onNewSample(expectedFrameRate)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `𝕄 forward scaled frame rate to observer 𝕎 doFrame() {two frame timestamp, with display}`(
        @LongForgery timestampNs: Long,
        @FloatForgery(.5f, 4f) deviceRefreshRateScale: Float,
        forge: Forge
    ) {
        // Given
        val mockWindowMgr = mock<WindowManager>()
        val mockDisplay = mock<Display>()
        whenever(appContext.mockInstance.getSystemService(Context.WINDOW_SERVICE))
            .doReturn(mockWindowMgr)
        whenever(mockWindowMgr.defaultDisplay) doReturn mockDisplay
        whenever(mockDisplay.refreshRate) doReturn (60 * deviceRefreshRateScale)

        val oneSecondScaled = ONE_SECOND_NS / deviceRefreshRateScale
        val frameDurationNs = forge.aLong(
            min = (oneSecondScaled / VitalFrameCallback.VALID_FPS_RANGE.endInclusive).toLong(),
            max = (oneSecondScaled / VitalFrameCallback.VALID_FPS_RANGE.start).toLong()
        )
        val expectedRawFrameRate = ONE_SECOND_NS.toDouble() / frameDurationNs.toDouble()
        val expectedFrameRate = expectedRawFrameRate / deviceRefreshRateScale

        // When
        testedFrameCallback.doFrame(timestampNs)
        testedFrameCallback.doFrame(timestampNs + frameDurationNs)

        // Then
        argumentCaptor<Double> {
            verify(mockObserver).onNewSample(capture())
            assertThat(firstValue).isCloseTo(expectedFrameRate, offset(0.0001))
        }
    }

    @Test
    fun `𝕄 schedule callback 𝕎 doFrame()`(
        @LongForgery timestampNs: Long
    ) {
        // Given

        // When
        testedFrameCallback.doFrame(timestampNs)

        // Then
        verify(mockChoreographer).postFrameCallback(testedFrameCallback)
    }

    @Test
    fun `𝕄 do nothing 𝕎 doFrame() {illegal exception when posting frame}`(
        @LongForgery timestampNs: Long,
        @StringForgery message: String
    ) {
        // Given
        val exception = IllegalStateException(message)
        whenever(mockChoreographer.postFrameCallback(testedFrameCallback)) doThrow exception

        // When
        testedFrameCallback.doFrame(timestampNs)

        // Then
    }

    @Test
    fun `𝕄 not schedule callback 𝕎 doFrame() {keepRunning returns false}`(
        @LongForgery timestampNs: Long
    ) {
        // Given
        testedFrameCallback = VitalFrameCallback(appContext.mockInstance, mockObserver) { false }

        // When
        testedFrameCallback.doFrame(timestampNs)

        // Then
        verify(mockChoreographer, never()).postFrameCallback(any())
    }

    companion object {
        const val ONE_MILLISECOND_NS: Long = 1000L * 1000L
        const val QUARTER_SECOND_NS: Long = 250L * 1000L * 1000L
        const val ONE_SECOND_NS: Long = 1000L * 1000L * 1000L
        const val TEN_SECOND_NS: Long = 10L * ONE_SECOND_NS
        const val ONE_MINUTE_NS: Long = 60L * ONE_SECOND_NS

        val logger = InternalLoggerTestConfiguration()
        val appContext = ApplicationContextTestConfiguration(Application::class.java)

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext, logger)
        }
    }
}
