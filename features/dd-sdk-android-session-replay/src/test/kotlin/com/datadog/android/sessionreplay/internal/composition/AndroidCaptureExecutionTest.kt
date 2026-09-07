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
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(ForgeConfigurator::class)
internal class AndroidCaptureExecutionTest {

    @Test
    fun `M execute and cancel main-thread task W handler accepts task`() {
        // Given
        val handler = mock<Handler>()
        whenever(handler.post(any<Runnable>())).thenReturn(true)
        val executor = HandlerCaptureMainThreadExecutor(handler)
        var executed = false

        // When
        val work = executor.execute { executed = true }
        val runnable = argumentCaptor<Runnable>()
        verify(handler).post(runnable.capture())
        runnable.firstValue.run()
        work.cancel()

        // Then
        assertThat(executed).isTrue()
        verify(handler).removeCallbacks(runnable.firstValue)
    }

    @Test
    fun `M schedule cancel and shutdown expiry W use scheduled executor`(
        @LongForgery(min = 0L) fakeDelayNs: Long
    ) {
        // Given
        val executorService = mock<ScheduledExecutorService>()
        val future = mock<ScheduledFuture<Any>>()
        whenever(
            executorService.schedule(any<Runnable>(), eq(fakeDelayNs), eq(TimeUnit.NANOSECONDS))
        ).thenReturn(future)
        val scheduler = ScheduledExecutorCaptureTaskScheduler(
            executorService,
            mock<InternalLogger>()
        )

        // When
        val work = scheduler.schedule(fakeDelayNs) {}
        work.cancel()
        scheduler.shutdown()

        // Then
        verify(future).cancel(false)
        verify(executorService).shutdownNow()
    }
}
