/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.vitals

import android.os.Build
import androidx.metrics.performance.FrameData
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import com.datadog.android.rum.internal.domain.FrameMetricsData
import com.datadog.android.rum.internal.vitals.JankStatsActivityLifecycleListenerTest.Companion.MAX_FPS
import com.datadog.android.rum.internal.vitals.JankStatsActivityLifecycleListenerTest.Companion.MIN_FPS
import com.datadog.android.rum.internal.vitals.JankStatsActivityLifecycleListenerTest.Companion.ONE_MILLISECOND_NS
import com.datadog.android.rum.internal.vitals.JankStatsActivityLifecycleListenerTest.Companion.ONE_SECOND_NS
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.DoubleForgery
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
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
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
internal class FPSVitalListenerTest {

    @Mock
    lateinit var mockObserver: VitalObserver

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private val mockBuildSdkVersionProvider: BuildSdkVersionProvider = mock {
        on { version } doReturn Build.VERSION_CODES.VANILLA_ICE_CREAM
    }

    private lateinit var listenerUnderTest: FPSVitalListener

    @BeforeEach
    fun `set up`() {
        listenerUnderTest = FPSVitalListener(mockObserver, mockBuildSdkVersionProvider)
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
        listenerUnderTest.onFrame(frameData)

        // Then
        verify(mockObserver).onNewSample(eq(expectedFrameRate, 0.0001))
    }

    fun `M do nothing W onFrame() {zero ns duration}`(
        @LongForgery timestampNs: Long,
        @BoolForgery isJank: Boolean
    ) {
        // Given
        val frameData = FrameData(timestampNs, 0L, isJank, emptyList())

        // When
        listenerUnderTest.onFrame(frameData)

        // Then
        verify(mockObserver, never()).onNewSample(any())
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

        whenever(mockBuildSdkVersionProvider.version) doReturn Build.VERSION_CODES.S

        listenerUnderTest.onFrameMetricsData(
            FrameMetricsData(
                deadline = (ONE_SECOND_NS / displayRefreshRate).toLong()
            )
        )

        // When
        listenerUnderTest.onFrame(frameData)

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

        whenever(mockBuildSdkVersionProvider.version) doReturn Build.VERSION_CODES.R

        listenerUnderTest.onFrameMetricsData(
            FrameMetricsData(
                displayRefreshRate = displayRefreshRate
            )
        )

        // When
        listenerUnderTest.onFrame(frameData)

        // Then
        if (expectedFrameRate * refreshRateMultiplier > MIN_FPS) {
            verify(mockObserver).onNewSample(eq(min(expectedFrameRate * refreshRateMultiplier, MAX_FPS), 0.0001))
        } else {
            verify(mockObserver, never()).onNewSample(any())
        }
    }
}
