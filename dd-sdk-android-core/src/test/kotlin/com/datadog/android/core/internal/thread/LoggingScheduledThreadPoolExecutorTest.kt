/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.v2.api.InternalLogger
import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.Forge
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.util.concurrent.CancellationException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class LoggingScheduledThreadPoolExecutorTest :
    AbstractLoggingExecutorServiceTest<ScheduledThreadPoolExecutor>() {

    override fun createTestedExecutorService(): ScheduledThreadPoolExecutor {
        return LoggingScheduledThreadPoolExecutor(1, mockInternalLogger)
    }

    @Test
    fun `𝕄 log nothing 𝕎 schedule() { task completes normally }`() {
        // When
        val futureTask = testedExecutor.schedule({
            // no-op
        }, 1, TimeUnit.MILLISECONDS)
        Thread.sleep(DEFAULT_SLEEP_DURATION_MS)

        // Then
        assertThat(futureTask.isDone).isTrue

        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `𝕄 log nothing 𝕎 schedule() { worker thread was interrupted }`() {
        // When
        val futureTask = testedExecutor.submit {
            Thread.currentThread().interrupt()
        }
        Thread.sleep(DEFAULT_SLEEP_DURATION_MS)

        // Then
        assertThat(futureTask.isDone).isTrue

        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `𝕄 log error + exception 𝕎 schedule() { task throws an exception }`(
        forge: Forge
    ) {
        // Given
        val throwable = forge.aThrowable()

        // When
        val futureTask = testedExecutor.schedule({
            throw throwable
        }, 1, TimeUnit.MILLISECONDS)
        Thread.sleep(DEFAULT_SLEEP_DURATION_MS)

        // Then
        assertThat(futureTask.isDone).isTrue

        verify(mockInternalLogger)
            .log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                ERROR_UNCAUGHT_EXECUTION_EXCEPTION,
                throwable
            )
    }

    @Test
    fun `𝕄 log error + exception 𝕎 schedule() { task is cancelled }`() {
        // When
        val futureTask = testedExecutor.schedule({
            Thread.sleep(500)
        }, 1, TimeUnit.MILLISECONDS)
        futureTask.cancel(true)
        Thread.sleep(DEFAULT_SLEEP_DURATION_MS)

        // Then
        assertThat(futureTask.isCancelled).isTrue

        verify(mockInternalLogger)
            .log(
                eq(InternalLogger.Level.ERROR),
                eq(listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY)),
                eq(ERROR_UNCAUGHT_EXECUTION_EXCEPTION),
                isA<CancellationException>(),
                eq(false)
            )
    }
}
