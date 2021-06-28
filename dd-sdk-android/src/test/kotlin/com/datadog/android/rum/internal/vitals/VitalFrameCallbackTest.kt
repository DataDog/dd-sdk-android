/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import android.view.Choreographer
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
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
internal class VitalFrameCallbackTest {

    lateinit var testedFrameCallback: Choreographer.FrameCallback

    @Mock
    lateinit var mockObserver: VitalObserver

    @Mock
    lateinit var mockChoreographer: Choreographer

    @BeforeEach
    fun `set up`() {
        testedFrameCallback = VitalFrameCallback(mockObserver) { true }

        Choreographer::class.java.setStaticValue(
            "sThreadInstance",
            object : ThreadLocal<Choreographer>() {
                override fun initialValue(): Choreographer {
                    return mockChoreographer
                }
            }
        )
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
    fun `𝕄 do nothing 𝕎 doFrame() {too small duration}`(
        @LongForgery timestampNs: Long,
        @LongForgery(1, ONE_MILLISSECOND_NS) frameDurationNs: Long
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
        @LongForgery(ONE_MILLISSECOND_NS, ONE_SECOND_NS) frameDurationNs: Long
    ) {
        // Given
        val expectedFrameRate = ONE_SECOND_NS.toDouble() / frameDurationNs.toDouble()

        // When
        testedFrameCallback.doFrame(timestampNs)
        testedFrameCallback.doFrame(timestampNs + frameDurationNs)

        // Then
        verify(mockObserver).onNewSample(expectedFrameRate)
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
    fun `𝕄 not schedule callback 𝕎 doFrame() {keepRunning returns false}`(
        @LongForgery timestampNs: Long
    ) {
        // Given
        testedFrameCallback = VitalFrameCallback(mockObserver) { false }

        // When
        testedFrameCallback.doFrame(timestampNs)

        // Then
        verify(mockChoreographer, never()).postFrameCallback(any())
    }

    companion object {
        const val ONE_MILLISSECOND_NS: Long = 1000L * 1000L
        const val ONE_SECOND_NS: Long = 1000L * 1000L * 1000L
        const val TEN_SECOND_NS: Long = 10L * ONE_SECOND_NS
        const val ONE_MINUTE_NS: Long = 60L * ONE_SECOND_NS
    }
}
