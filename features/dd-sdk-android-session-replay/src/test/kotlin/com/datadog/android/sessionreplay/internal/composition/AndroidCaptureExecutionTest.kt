/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.os.Handler
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(ForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class AndroidCaptureExecutionTest {

    @Test
    fun `M post to main handler W execute()`() {
        // Given
        val mockHandler: Handler = mock()
        whenever(mockHandler.post(any<Runnable>())).thenReturn(true)
        val testedExecutor = HandlerCaptureMainThreadExecutor(mockHandler)

        // When
        testedExecutor.execute {}

        // Then
        verify(mockHandler).post(isA<Runnable>())
    }

    @Test
    fun `M invoke the action W run() { posted main-thread task }`() {
        // Given
        val mockHandler: Handler = mock()
        val fakeRunnable = argumentCaptor<Runnable>()
        whenever(mockHandler.post(fakeRunnable.capture())).thenReturn(true)
        val testedExecutor = HandlerCaptureMainThreadExecutor(mockHandler)
        var fakeExecuted = false
        testedExecutor.execute { fakeExecuted = true }

        // When
        fakeRunnable.firstValue.run()

        // Then
        assertThat(fakeExecuted).isTrue()
    }

    @Test
    fun `M remove posted task W cancel()`() {
        // Given
        val mockHandler: Handler = mock()
        val fakeRunnable = argumentCaptor<Runnable>()
        whenever(mockHandler.post(fakeRunnable.capture())).thenReturn(true)
        val testedExecutor = HandlerCaptureMainThreadExecutor(mockHandler)
        val fakeWork = testedExecutor.execute {}

        // When
        fakeWork.cancel()

        // Then
        verify(mockHandler).removeCallbacks(fakeRunnable.firstValue)
    }

    @Test
    fun `M submit task with nanosecond delay W schedule()`(@LongForgery(min = 0L) fakeDelayNs: Long) {
        // Given
        val mockExecutor: ScheduledExecutorService = mock()
        val testedScheduler = ExecutorCaptureTaskScheduler(mockExecutor, mock<InternalLogger>())

        // When
        testedScheduler.schedule(fakeDelayNs) {}

        // Then
        verify(mockExecutor).schedule(isA<Runnable>(), eq(fakeDelayNs), eq(TimeUnit.NANOSECONDS))
    }

    @Test
    fun `M cancel scheduled future without interruption W cancel()`(@LongForgery(min = 0L) fakeDelayNs: Long) {
        // Given
        val mockExecutor: ScheduledExecutorService = mock()
        val mockFuture: ScheduledFuture<Any> = mock()
        whenever(mockExecutor.schedule(any<Runnable>(), eq(fakeDelayNs), eq(TimeUnit.NANOSECONDS)))
            .thenReturn(mockFuture)
        val testedScheduler = ExecutorCaptureTaskScheduler(mockExecutor, mock<InternalLogger>())
        val fakeWork = testedScheduler.schedule(fakeDelayNs) {}

        // When
        fakeWork.cancel()

        // Then
        verify(mockFuture).cancel(false)
    }

    @Test
    fun `M stop the expiry executor W shutdown()`() {
        // Given
        val mockExecutor: ScheduledExecutorService = mock()
        val testedScheduler = ExecutorCaptureTaskScheduler(mockExecutor, mock<InternalLogger>())

        // When
        testedScheduler.shutdown()

        // Then
        verify(mockExecutor).shutdownNow()
    }
}
