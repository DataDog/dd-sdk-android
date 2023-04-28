/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.InternalLogger
import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.quality.Strictness
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal abstract class AbstractLoggingExecutorServiceTest<T : ExecutorService> {

    lateinit var testedExecutor: T

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        testedExecutor = createTestedExecutorService()
    }

    @AfterEach
    fun `tear down`() {
        testedExecutor.shutdownNow()
    }

    abstract fun createTestedExecutorService(): T

    // region execute

    @Test
    fun `𝕄 log nothing 𝕎 execute() { task completes normally }`() {
        // When
        testedExecutor.execute {
            // no-op
        }
        Thread.sleep(DEFAULT_SLEEP_DURATION_MS)

        // Then
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `𝕄 log nothing 𝕎 execute() { worker thread was interrupted }`() {
        // When
        testedExecutor.execute {
            Thread.currentThread().interrupt()
        }
        Thread.sleep(DEFAULT_SLEEP_DURATION_MS)

        // Then
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `𝕄 log error + exception 𝕎 execute() { task throws an exception }`(
        forge: Forge
    ) {
        // Given
        val throwable = forge.aThrowable()

        // When
        testedExecutor.execute {
            throw throwable
        }
        Thread.sleep(DEFAULT_SLEEP_DURATION_MS)

        // Then
        verify(mockInternalLogger)
            .log(
                InternalLogger.Level.ERROR,
                targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                ERROR_UNCAUGHT_EXECUTION_EXCEPTION,
                throwable
            )
    }

    // endregion

    // region submit

    @Test
    fun `𝕄 log nothing 𝕎 submit() { task completes normally }`() {
        // When
        val futureTask = testedExecutor.submit {
            // no-op
        }
        Thread.sleep(DEFAULT_SLEEP_DURATION_MS)

        // Then
        assertThat(futureTask.isDone).isTrue

        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `𝕄 log nothing 𝕎 submit() { worker thread was interrupted }`() {
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
    fun `𝕄 log error + exception 𝕎 submit() { task throws an exception }`(
        forge: Forge
    ) {
        // Given
        val throwable = forge.aThrowable()

        // When
        val futureTask = testedExecutor.submit {
            throw throwable
        }
        Thread.sleep(DEFAULT_SLEEP_DURATION_MS)

        // Then
        assertThat(futureTask.isDone).isTrue

        verify(mockInternalLogger)
            .log(
                InternalLogger.Level.ERROR,
                targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                ERROR_UNCAUGHT_EXECUTION_EXCEPTION,
                throwable
            )
    }

    @Test
    fun `𝕄 log error + exception 𝕎 submit() { task was cancelled }`() {
        // When
        val futureTask = testedExecutor.submit {
            Thread.sleep(500)
        }
        futureTask.cancel(true)
        Thread.sleep(DEFAULT_SLEEP_DURATION_MS)

        // Then
        assertThat(futureTask.isCancelled).isTrue

        verify(mockInternalLogger)
            .log(
                eq(InternalLogger.Level.ERROR),
                targets = eq(listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY)),
                eq(ERROR_UNCAUGHT_EXECUTION_EXCEPTION),
                isA<CancellationException>()
            )
    }

    // endregion

    companion object {
        const val DEFAULT_SLEEP_DURATION_MS = 50L
    }
}
