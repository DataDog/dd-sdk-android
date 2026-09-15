/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.configuration.BackPressureMitigation
import com.datadog.android.core.configuration.BackPressureStrategy
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ThreadPoolExecutorExtTest {

    @Mock
    lateinit var testedMockExecutor: ThreadPoolExecutor

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @BeforeEach
    fun `set up`() {
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos()).thenAnswer { System.nanoTime() }
    }

    @Test
    fun `M return false W waitToIdle { timeout reached }`(
        @LongForgery(min = 0, max = 500) fakeTimeout: Long,
        forge: Forge
    ) {
        // GIVEN
        val fakeTaskCount = forge.aLong(min = 2, max = 10)
        val fakeCompletedCount = forge.aLong(min = 0, max = fakeTaskCount - 1)
        whenever(testedMockExecutor.taskCount).thenReturn(fakeTaskCount)
        whenever(testedMockExecutor.completedTaskCount).thenReturn(fakeCompletedCount)

        // WHEN
        val isIdled = testedMockExecutor.waitToIdle(fakeTimeout, mockInternalLogger, mockTimeProvider)

        // THEN
        assertThat(isIdled).isFalse()
    }

    @Test
    fun `M wait max timeout milliseconds W waitToIdle { executor not idled }`(
        @LongForgery(min = 500, max = 1000) fakeTimeout: Long,
        forge: Forge
    ) {
        // GIVEN
        val fakeTaskCount = forge.aLong(min = 2, max = 10)
        val fakeCompletedCount = forge.aLong(min = 0, max = fakeTaskCount - 1)
        whenever(testedMockExecutor.taskCount).thenReturn(fakeTaskCount)
        whenever(testedMockExecutor.completedTaskCount).thenReturn(fakeCompletedCount)

        // WHEN
        val duration = measureTimeMillis {
            testedMockExecutor.waitToIdle(fakeTimeout, mockInternalLogger, mockTimeProvider)
        }

        // THEN
        assertThat(duration).isCloseTo(fakeTimeout, Offset.offset(130L))
    }

    @Test
    fun `M return true W waitToIdle { executor idled }`(
        @LongForgery(min = 0, max = 500) fakeTimeout: Long,
        @LongForgery(min = 0, max = 10) fakeTaskCount: Long

    ) {
        // GIVEN
        whenever(testedMockExecutor.taskCount).thenReturn(fakeTaskCount)
        whenever(testedMockExecutor.completedTaskCount)
            .thenReturn(fakeTaskCount)

        // WHEN
        val isIdled = testedMockExecutor.waitToIdle(fakeTimeout, mockInternalLogger, mockTimeProvider)

        // THEN
        assertThat(isIdled).isTrue()
    }

    @Test
    fun `M return true W waitToIdle { executor idled after multiple iterations }`(
        @LongForgery(
            min = MAX_SLEEP_DURATION_IN_MS * 3,
            max = MAX_SLEEP_DURATION_IN_MS * 4
        ) fakeTimeout: Long,
        @LongForgery(min = 0, max = 10) fakeTaskCount: Long

    ) {
        // GIVEN
        whenever(testedMockExecutor.taskCount).thenReturn(fakeTaskCount)
        whenever(testedMockExecutor.completedTaskCount)
            .thenReturn(fakeTaskCount / 2).thenReturn(fakeTaskCount)

        // WHEN
        val isIdled = testedMockExecutor.waitToIdle(fakeTimeout, mockInternalLogger, mockTimeProvider)

        // THEN
        assertThat(isIdled).isTrue()
    }

    @Test
    fun `M return false W waitToIdle { timeout is negative, executor not idled }`(
        @LongForgery(min = Long.MIN_VALUE, max = 0) fakeTimeout: Long,
        forge: Forge
    ) {
        // GIVEN
        val fakeTaskCount = forge.aLong(min = 2, max = 10)
        val fakeCompletedCount = forge.aLong(min = 0, max = fakeTaskCount - 1)
        whenever(testedMockExecutor.taskCount).thenReturn(fakeTaskCount)
        whenever(testedMockExecutor.completedTaskCount).thenReturn(fakeCompletedCount)

        // WHEN
        val isIdled = testedMockExecutor.waitToIdle(fakeTimeout, mockInternalLogger, mockTimeProvider)

        // THEN
        assertThat(isIdled).isFalse()
    }

    @Test
    fun `M return true W waitToIdle { timeout is negative, executor idled }`(
        @LongForgery(min = Long.MIN_VALUE, max = 0) fakeTimeout: Long,
        @LongForgery(min = 0, max = 10) fakeTaskCount: Long
    ) {
        // GIVEN
        whenever(testedMockExecutor.taskCount).thenReturn(fakeTaskCount)
        whenever(testedMockExecutor.completedTaskCount)
            .thenReturn(fakeTaskCount)

        // WHEN
        val isIdled = testedMockExecutor.waitToIdle(fakeTimeout, mockInternalLogger, mockTimeProvider)

        // THEN
        assertThat(isIdled).isTrue()
    }

    @Test
    fun `M return true W waitToIdle { more tasks where added between sleep intervals }`(
        @LongForgery(
            min = MAX_SLEEP_DURATION_IN_MS * 3,
            max = MAX_SLEEP_DURATION_IN_MS * 4
        ) fakeTimeout: Long,
        @LongForgery(min = 0, max = 10) fakeTaskCount: Long
    ) {
        // GIVEN
        whenever(testedMockExecutor.taskCount)
            .thenReturn(fakeTaskCount)
            .thenReturn(fakeTaskCount + 2)
        whenever(testedMockExecutor.completedTaskCount)
            .thenReturn(fakeTaskCount / 2)
            .thenReturn(fakeTaskCount + 2)

        // WHEN
        val isIdled = testedMockExecutor.waitToIdle(fakeTimeout, mockInternalLogger, mockTimeProvider)

        // THEN
        assertThat(isIdled).isTrue()
    }

    // region enableIdleThreadTimeout

    private fun fakeBackPressureStrategy() = BackPressureStrategy(
        32,
        {},
        {},
        BackPressureMitigation.DROP_OLDEST
    )

    @Test
    fun `M enable core thread timeout W enableIdleThreadTimeout() {BackPressureExecutorService}`(
        @StringForgery fakeExecutorContext: String
    ) {
        // Given
        val testedExecutor = BackPressureExecutorService(
            mockInternalLogger,
            fakeExecutorContext,
            fakeBackPressureStrategy(),
            mockTimeProvider
        )

        // When
        testedExecutor.enableIdleThreadTimeout()

        // Then
        assertThat(testedExecutor.allowsCoreThreadTimeOut()).isTrue()
        assertThat(testedExecutor.getKeepAliveTime(TimeUnit.MILLISECONDS))
            .isEqualTo(IDLE_THREAD_KEEP_ALIVE_MS)
        testedExecutor.shutdownNow()
    }

    @Test
    fun `M enable core thread timeout W enableIdleThreadTimeout() {LoggingScheduledThreadPoolExecutor}`(
        @StringForgery fakeExecutorContext: String
    ) {
        // Given
        val testedExecutor = LoggingScheduledThreadPoolExecutor(
            1,
            fakeExecutorContext,
            mockInternalLogger,
            fakeBackPressureStrategy()
        )

        // When
        testedExecutor.enableIdleThreadTimeout()

        // Then
        assertThat(testedExecutor.allowsCoreThreadTimeOut()).isTrue()
        assertThat(testedExecutor.getKeepAliveTime(TimeUnit.MILLISECONDS))
            .isEqualTo(IDLE_THREAD_KEEP_ALIVE_MS)
        testedExecutor.shutdownNow()
    }

    @Test
    fun `M do nothing W enableIdleThreadTimeout() {foreign executor}`() {
        // Given a third-party executor, as a customer FlushableExecutorService.Factory may return
        val testedExecutor: ExecutorService = ThreadPoolExecutor(
            1,
            1,
            1_000L,
            TimeUnit.MILLISECONDS,
            LinkedBlockingQueue()
        )

        // When
        testedExecutor.enableIdleThreadTimeout()

        // Then
        assertThat((testedExecutor as ThreadPoolExecutor).allowsCoreThreadTimeOut()).isFalse()
        assertThat(testedExecutor.getKeepAliveTime(TimeUnit.MILLISECONDS)).isEqualTo(1_000L)
        testedExecutor.shutdownNow()
    }

    // endregion

    // region JDK guarantees this design depends on

    @Test
    fun `M still run pending delayed task W pool idles past keep alive`(
        @StringForgery fakeExecutorContext: String
    ) {
        // Given a scheduled pool whose worker may time out far sooner than the task's delay.
        // ThreadPoolExecutor#getTask() will not let the LAST worker exit while the queue is
        // non-empty, and a not-yet-due delayed task counts as non-empty. If that guarantee
        // ever breaks, sparse scheduled work would be silently dropped.
        val testedExecutor = LoggingScheduledThreadPoolExecutor(
            1,
            fakeExecutorContext,
            mockInternalLogger,
            fakeBackPressureStrategy()
        )
        testedExecutor.setKeepAliveTime(20L, TimeUnit.MILLISECONDS)
        testedExecutor.allowCoreThreadTimeOut(true)
        val latch = CountDownLatch(1)

        // When
        testedExecutor.schedule({ latch.countDown() }, 300L, TimeUnit.MILLISECONDS)

        // Then
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
        testedExecutor.shutdownNow()
    }

    @Test
    fun `M reclaim worker W pool idles past keep alive with empty queue`(
        @StringForgery fakeExecutorContext: String
    ) {
        // Given
        val testedExecutor = LoggingScheduledThreadPoolExecutor(
            1,
            fakeExecutorContext,
            mockInternalLogger,
            fakeBackPressureStrategy()
        )
        testedExecutor.setKeepAliveTime(20L, TimeUnit.MILLISECONDS)
        testedExecutor.allowCoreThreadTimeOut(true)
        val latch = CountDownLatch(1)
        testedExecutor.schedule({ latch.countDown() }, 0L, TimeUnit.MILLISECONDS)
        check(latch.await(5, TimeUnit.SECONDS))

        // When the queue drains, the worker is no longer retained
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (testedExecutor.poolSize > 0 && System.nanoTime() < deadline) {
            Thread.sleep(20L)
        }

        // Then
        assertThat(testedExecutor.poolSize).isZero()
        testedExecutor.shutdownNow()
    }

    @Test
    fun `M retain worker W periodic task pending across keep alive windows`(
        @StringForgery fakeExecutorContext: String
    ) {
        // Given a periodic task always leaves itself queued, so its worker must never be reclaimed.
        // This is what makes the change a no-op for rum-vital, the flags flush and client stats.
        val testedExecutor = LoggingScheduledThreadPoolExecutor(
            1,
            fakeExecutorContext,
            mockInternalLogger,
            fakeBackPressureStrategy()
        )
        testedExecutor.setKeepAliveTime(20L, TimeUnit.MILLISECONDS)
        testedExecutor.allowCoreThreadTimeOut(true)
        val runs = CountDownLatch(3)

        // When
        testedExecutor.scheduleWithFixedDelay(
            { runs.countDown() },
            0L,
            60L,
            TimeUnit.MILLISECONDS
        )

        // Then the task keeps firing across several keep-alive windows, and a worker is still held
        assertThat(runs.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(testedExecutor.poolSize).isEqualTo(1)
        testedExecutor.shutdownNow()
    }

    // endregion
}
