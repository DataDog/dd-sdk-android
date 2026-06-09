/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.configuration.BackPressureStrategy
import com.datadog.android.internal.thread.NamedCallable
import com.datadog.android.internal.thread.NamedExecutionUnit
import com.datadog.android.internal.thread.NamedRunnable
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever

internal class BackPressureExecutorServiceTest :
    AbstractExecutorServiceTest<BackPressureExecutorService>() {

    @StringForgery
    lateinit var fakeExecutorContext: String

    override fun createTestedExecutorService(
        forge: Forge,
        backPressureStrategy: BackPressureStrategy,
        timeProvider: TimeProvider
    ): BackPressureExecutorService {
        return BackPressureExecutorService(
            mockInternalLogger,
            fakeExecutorContext,
            backPressureStrategy,
            timeProvider
        )
    }

    @Test
    fun `M use DatadogThreadFactory W constructor()`() {
        // Then
        assertThat(testedExecutor.threadFactory).isInstanceOf(DatadogThreadFactory::class.java)
    }

    @Test
    fun `M wrap named runnable W submit()`(
        @StringForgery fakeName: String
    ) {
        // Given
        val fakeRunnable = NamedRunnable(fakeName) { }

        // When
        val future = testedExecutor.submit(fakeRunnable)

        // Then
        check(future is NamedExecutionUnit)
        assertThat(future.name).isEqualTo(fakeRunnable.name)
    }

    @Test
    fun `M wrap named callable W submit()`(
        @StringForgery fakeName: String
    ) {
        // Given
        val fakeCallable = NamedCallable(fakeName) { }

        // When
        val future = testedExecutor.submit(fakeCallable)

        // Then
        check(future is NamedExecutionUnit)
        assertThat(future.name).isEqualTo(fakeCallable.name)
    }

    @Test
    fun `M report dump W submit or execute()`(
        @StringForgery fakeRunnableName: String,
        @StringForgery fakeCallableName: String
    ) {
        // Given
        val fakeRunnable = NamedRunnable(fakeRunnableName) { }
        val fakeCallable = NamedCallable(fakeCallableName) { }
        whenever(mockTimeProvider.getDeviceTimestampMillis()) doReturn System.currentTimeMillis()

        // When
        // submit never-ending task, so that queue is not decreasing
        testedExecutor.submit {
            Thread.sleep(Int.MAX_VALUE.toLong())
        }

        // fill up the queue
        repeat(fakeBackPressureCapacity - 4) {
            testedExecutor.submit { }
        }
        testedExecutor.execute(fakeRunnable)
        testedExecutor.submit(fakeRunnable)
        testedExecutor.submit(fakeCallable)

        // hit the limit
        testedExecutor.execute { }

        // Then
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.WARN,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            message = "BackPressuredBlockingQueue reached capacity:$fakeBackPressureCapacity",
            additionalProperties = mapOf(
                "backpressure" to mapOf(
                    "capacity" to fakeBackPressureCapacity,
                    "dump" to mapOf(
                        fakeRunnable.name to 2,
                        fakeCallable.name to 1
                    )
                ),
                "executor.context" to fakeExecutorContext
            )
        )
    }
}
