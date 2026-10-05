/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.collector

import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class LooperTest {

    private lateinit var testedLooper: Looper

    @Mock
    lateinit var mockExecutor: ScheduledExecutorService

    @Mock
    lateinit var mockGate: Gate

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @StringForgery
    lateinit var fakeName: String

    @Forgery
    lateinit var fakeRumContext: RumContext

    @LongForgery(min = 1L, max = 60_000L)
    var fakeIntervalMs: Long = 0L

    @IntForgery(min = 0, max = 1000)
    var fakeGeneration: Int = 0

    @BeforeEach
    fun `set up`() {
        testedLooper = Looper(
            name = fakeName,
            gate = mockGate,
            scheduledExecutorService = mockExecutor,
            internalLogger = mockInternalLogger
        )
    }

    private fun captureScheduledRunnable(): Runnable {
        val captor = argumentCaptor<Runnable>()
        verify(mockExecutor).schedule(captor.capture(), eq(fakeIntervalMs), eq(TimeUnit.MILLISECONDS))
        return captor.lastValue
    }

    @Test
    fun `M schedule via executor W start()`() {
        // When
        testedLooper.start(fakeGeneration, fakeIntervalMs, TimeUnit.MILLISECONDS) { }

        // Then
        verify(mockExecutor).schedule(any<Runnable>(), eq(fakeIntervalMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M invoke operation and reschedule W tick fires { allowed }`() {
        // Given
        var received: RumContext? = null
        whenever(mockGate.runIfGateOpen(eq(fakeGeneration), any())) doAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            (inv.arguments[1] as (RumContext) -> Unit).invoke(fakeRumContext)
            true
        }
        testedLooper.start(fakeGeneration, fakeIntervalMs, TimeUnit.MILLISECONDS) { received = it }
        val runnable = captureScheduledRunnable()

        // When
        runnable.run()

        // Then
        assertThat(received).isEqualTo(fakeRumContext)
        verify(mockExecutor, times(2)).schedule(any<Runnable>(), eq(fakeIntervalMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M not invoke operation nor reschedule W tick fires { not allowed }`() {
        // Given
        whenever(mockGate.runIfGateOpen(eq(fakeGeneration), any())) doReturn false
        var invoked = false
        testedLooper.start(fakeGeneration, fakeIntervalMs, TimeUnit.MILLISECONDS) { invoked = true }
        val runnable = captureScheduledRunnable()

        // When
        runnable.run()

        // Then
        assertThat(invoked).isFalse()
        verify(mockExecutor).schedule(any<Runnable>(), eq(fakeIntervalMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M log error and reschedule W tick fires { operation throws }`() {
        // Given
        val fakeError = RuntimeException("operation failure")
        whenever(mockGate.runIfGateOpen(eq(fakeGeneration), any()))
            .doThrow(fakeError)
            .doAnswer { inv ->
                @Suppress("UNCHECKED_CAST")
                (inv.arguments[1] as (RumContext) -> Unit).invoke(fakeRumContext)
                true
            }
        testedLooper.start(fakeGeneration, fakeIntervalMs, TimeUnit.MILLISECONDS) { }
        val runnable = captureScheduledRunnable()

        // When
        runnable.run()

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.USER),
            Looper.ERROR_SAMPLING_FAILED,
            fakeError
        )
        verify(mockExecutor, times(2)).schedule(any<Runnable>(), eq(fakeIntervalMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M reschedule once then stop W tick fires { operation throws, generation no longer allowed }`() {
        // Given
        // gate.runIfAllowed is only consulted once per tick now, so a thrown operation always
        // reschedules one more tick; that next tick is the one which observes the gate no
        // longer allows the generation and stops the chain.
        val fakeError = RuntimeException("operation failure")
        whenever(mockGate.runIfGateOpen(eq(fakeGeneration), any()))
            .doThrow(fakeError)
            .doReturn(false)
        testedLooper.start(fakeGeneration, fakeIntervalMs, TimeUnit.MILLISECONDS) { }
        val firstRunnable = captureScheduledRunnable()

        // When
        firstRunnable.run()

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.USER),
            Looper.ERROR_SAMPLING_FAILED,
            fakeError
        )
        val captor = argumentCaptor<Runnable>()
        verify(mockExecutor, times(2)).schedule(captor.capture(), eq(fakeIntervalMs), eq(TimeUnit.MILLISECONDS))

        // When the rescheduled tick fires
        captor.lastValue.run()

        // Then it stops, no third reschedule
        verify(mockExecutor, times(2)).schedule(any<Runnable>(), eq(fakeIntervalMs), eq(TimeUnit.MILLISECONDS))
    }
}
