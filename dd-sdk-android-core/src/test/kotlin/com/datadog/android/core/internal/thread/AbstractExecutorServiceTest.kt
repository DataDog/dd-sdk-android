/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.configuration.BackPressureMitigation
import com.datadog.android.core.configuration.BackPressureStrategy
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
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
internal abstract class AbstractExecutorServiceTest<T : ExecutorService> {

    lateinit var testedExecutor: T

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockOnThresholdReached: () -> Unit

    @Mock
    lateinit var mockOnItemDropped: (Any) -> Unit

    @IntForgery(8, 128)
    var fakeBackPressureCapacity: Int = 0

    @Forgery
    lateinit var fakeBackPressureMitigation: BackPressureMitigation

    lateinit var fakeBackpressureStrategy: BackPressureStrategy

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeBackpressureStrategy = BackPressureStrategy(
            fakeBackPressureCapacity,
            mockOnThresholdReached,
            mockOnItemDropped,
            fakeBackPressureMitigation
        )
        testedExecutor = createTestedExecutorService(forge, fakeBackpressureStrategy)
    }

    @AfterEach
    fun `tear down`() {
        testedExecutor.shutdownNow()
    }

    abstract fun createTestedExecutorService(forge: Forge, backPressureStrategy: BackPressureStrategy): T

    // region execute

    @Test
    fun `M log nothing W execute() { task completes normally }`() {
        // When
        testedExecutor.execute {
            // no-op
        }
        Thread.sleep(DEFAULT_SLEEP_DURATION_MS)

        // Then
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M log nothing W execute() { worker thread was interrupted }`() {
        // When
        testedExecutor.execute {
            Thread.currentThread().interrupt()
        }
        Thread.sleep(DEFAULT_SLEEP_DURATION_MS)

        // Then
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M log error + exception W execute() { task throws an exception }`(
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
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            ERROR_UNCAUGHT_EXECUTION_EXCEPTION,
            throwable
        )
    }

    // endregion

    // region submit

    @Test
    fun `M log nothing W submit() { task completes normally }`() {
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
    fun `M log nothing W submit() { worker thread was interrupted }`() {
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
    fun `M log error + exception W submit() { task throws an exception }`(
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

        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            ERROR_UNCAUGHT_EXECUTION_EXCEPTION,
            throwable
        )
    }

    @Test
    fun `M log error + exception W submit() { task was cancelled }`() {
        // When
        val futureTask = testedExecutor.submit {
            Thread.sleep(500)
        }
        futureTask.cancel(true)
        Thread.sleep(DEFAULT_SLEEP_DURATION_MS)

        // Then
        assertThat(futureTask.isCancelled).isTrue

        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            ERROR_UNCAUGHT_EXECUTION_EXCEPTION,
            CancellationException::class.java
        )
    }

    // endregion

    companion object {
        const val DEFAULT_SLEEP_DURATION_MS = 50L
    }
}
