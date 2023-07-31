/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.api.InternalLogger
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import kotlin.system.measureTimeMillis

@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@ForgeConfiguration(value = BaseConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class ThreadExtTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Test
    fun `M sleep for given duration W sleepSafe()`(
        @LongForgery(min = 100L, max = 1000L) duration: Long
    ) {
        // When
        val wasInterrupted: Boolean
        val actualDuration = measureTimeMillis {
            wasInterrupted = sleepSafe(duration, mockInternalLogger)
        }

        // Then
        assertThat(wasInterrupted).isFalse()
        assertThat(actualDuration).isCloseTo(duration, Offset.offset(10L))
    }

    @Test
    fun `M swallow error W sleepSafe() {negative duration}`(
        @LongForgery(max = -1L) duration: Long
    ) {
        // When
        val wasInterrupted = sleepSafe(duration, mockInternalLogger)

        // Then
        assertThat(wasInterrupted).isFalse()
    }

    @Test
    fun `M swallow interruption W sleepSafe()`() {
        // Given
        val currentThread = Thread.currentThread()
        val otherThread = Thread {
            Thread.sleep(50L)
            currentThread.interrupt()
        }

        // When
        otherThread.start()
        val wasInterrupted = sleepSafe(250, mockInternalLogger)

        // Then
        assertThat(wasInterrupted).isTrue()
    }
}
