/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.collector

import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.utils.forge.Configurator
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.quality.Strictness
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DebouncerTest {

    private lateinit var testedDebouncer: Debouncer

    @Mock
    lateinit var mockExecutor: ScheduledExecutorService

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @StringForgery
    lateinit var fakeOperationName: String

    @LongForgery(min = 1L, max = 60_000L)
    var fakeDelayMs: Long = 0L

    @BeforeEach
    fun `set up`() {
        testedDebouncer = Debouncer(
            scheduledExecutorService = mockExecutor,
            internalLogger = mockInternalLogger,
            operationName = fakeOperationName
        )
    }

    private fun captureScheduledRunnable(): Runnable {
        val captor = argumentCaptor<Runnable>()
        verify(mockExecutor).schedule(captor.capture(), eq(fakeDelayMs), eq(TimeUnit.MILLISECONDS))
        return captor.lastValue
    }

    private fun captureLastScheduledRunnable(callCount: Int): Runnable {
        val captor = argumentCaptor<Runnable>()
        verify(mockExecutor, times(callCount))
            .schedule(captor.capture(), eq(fakeDelayMs), eq(TimeUnit.MILLISECONDS))
        return captor.lastValue
    }

    @Test
    fun `M schedule via executor W runDelayed()`() {
        // When
        testedDebouncer.runDelayed(fakeDelayMs) { }

        // Then
        verify(mockExecutor).schedule(any<Runnable>(), eq(fakeDelayMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M invoke call W scheduled tick fires`() {
        // Given
        var invoked = false
        testedDebouncer.runDelayed(fakeDelayMs) { invoked = true }
        val runnable = captureScheduledRunnable()

        // When
        runnable.run()

        // Then
        assertThat(invoked).isTrue()
    }

    @Test
    fun `M not invoke call W scheduled tick fires { cancelled first }`() {
        // Given
        var invoked = false
        testedDebouncer.runDelayed(fakeDelayMs) { invoked = true }
        val runnable = captureScheduledRunnable()

        // When
        testedDebouncer.cancel()
        runnable.run()

        // Then
        assertThat(invoked).isFalse()
    }

    @Test
    fun `M not invoke stale call W stale tick fires { superseded by a later runDelayed }`() {
        // Given
        var staleInvoked = false
        var nextInvoked = false
        testedDebouncer.runDelayed(fakeDelayMs) { staleInvoked = true }
        val staleRunnable = captureScheduledRunnable()

        // When
        testedDebouncer.runDelayed(fakeDelayMs) { nextInvoked = true }
        staleRunnable.run()

        // Then
        assertThat(staleInvoked).isFalse()
        assertThat(nextInvoked).isFalse()

        // When the latest scheduled tick fires
        val captor = argumentCaptor<Runnable>()
        verify(mockExecutor, times(2))
            .schedule(captor.capture(), eq(fakeDelayMs), eq(TimeUnit.MILLISECONDS))
        captor.lastValue.run()

        // Then
        assertThat(nextInvoked).isTrue()
    }

    @Test
    fun `M allow scheduling again W runDelayed() { called after cancel() }`() {
        // Given
        testedDebouncer.runDelayed(fakeDelayMs) { }
        captureScheduledRunnable()
        testedDebouncer.cancel()

        // When
        var invoked = false
        testedDebouncer.runDelayed(fakeDelayMs) { invoked = true }
        val runnable = captureLastScheduledRunnable(callCount = 2)
        runnable.run()

        // Then
        assertThat(invoked).isTrue()
    }

    @Test
    fun `M allow scheduling again W runDelayed() { called after it already fired }`() {
        // Given
        var firstInvoked = false
        testedDebouncer.runDelayed(fakeDelayMs) { firstInvoked = true }
        captureScheduledRunnable().run()
        assertThat(firstInvoked).isTrue()

        // When
        var secondInvoked = false
        testedDebouncer.runDelayed(fakeDelayMs) { secondInvoked = true }
        val runnable = captureLastScheduledRunnable(callCount = 2)
        runnable.run()

        // Then
        assertThat(secondInvoked).isTrue()
    }

    @Test
    fun `M do nothing W cancel() { nothing scheduled }`() {
        // When
        testedDebouncer.cancel()

        // Then
        verifyNoInteractions(mockExecutor)
    }
}
