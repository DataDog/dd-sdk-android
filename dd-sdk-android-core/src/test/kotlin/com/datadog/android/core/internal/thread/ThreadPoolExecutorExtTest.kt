/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
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
import java.util.concurrent.ThreadPoolExecutor
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
}
