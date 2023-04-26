/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import androidx.metrics.performance.FrameData
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.InternalLogger
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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

    @BeforeEach
    fun `set up`() {
        testedJankListener = JankStatsActivityLifecycleListener(mockObserver, mockInternalLogger)
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
    fun `𝕄 forward frame rate to observer 𝕎 doFrame() {two frame timestamp}`(
        @LongForgery timestampNs: Long,
        @LongForgery(ONE_MILLISSECOND_NS, ONE_SECOND_NS) frameDurationNs: Long,
        @BoolForgery isJank: Boolean
    ) {
        // Given
        val expectedFrameRate = ONE_SECOND_NS.toDouble() / frameDurationNs.toDouble()
        val frameData = FrameData(timestampNs, frameDurationNs, isJank, emptyList())

        // When
        testedJankListener.onFrame(frameData)

        // Then
        verify(mockObserver).onNewSample(expectedFrameRate)
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
        const val ONE_MILLISSECOND_NS: Long = 1000L * 1000L
        const val ONE_SECOND_NS: Long = 1000L * 1000L * 1000L
        const val TEN_SECOND_NS: Long = 10L * ONE_SECOND_NS
        const val ONE_MINUTE_NS: Long = 60L * ONE_SECOND_NS
    }
}
