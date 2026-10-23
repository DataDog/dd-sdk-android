/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.evaluation

import com.datadog.android.api.InternalLogger
import com.datadog.android.utils.verifyLog
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class EvaluationCallbackDispatcherTest {
    @Mock lateinit var mockLogger: InternalLogger

    @Mock lateinit var mockWorker: ExecutorService
    private lateinit var testedDispatcher: EvaluationCallbackDispatcher
    private val pending = ArrayDeque<Runnable>()

    @BeforeEach
    fun setUp() {
        doAnswer { pending.addLast(it.getArgument(0)); null }.whenever(mockWorker).execute(any())
        testedDispatcher = EvaluationCallbackDispatcher(mockLogger) { mockWorker }
    }

    @Test
    fun `M preserve order W execute() {callback reenters dispatcher}`() {
        // Given
        val delivered = mutableListOf<Int>()
        testedDispatcher.execute {
            delivered.add(1)
            testedDispatcher.execute { delivered.add(3) }
        }
        testedDispatcher.execute { delivered.add(2) }

        // When
        pending.removeFirst().run()

        // Then
        assertThat(delivered).containsExactly(1, 2, 3)
        assertThat(pending).isEmpty()
        verify(mockWorker).shutdown()
    }

    @Test
    fun `M continue delivery W execute() {customer callback throws}`() {
        // Given
        val fakeError = IllegalStateException("callback failed")
        var delivered = false
        testedDispatcher.execute { throw fakeError }
        testedDispatcher.execute { delivered = true }

        // When
        pending.removeFirst().run()

        // Then
        assertThat(delivered).isTrue()
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            "A feature flags evaluation callback failed.",
            fakeError
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M retain pending delivery W execute() {submission fails}`(securityFailure: Boolean) {
        // Given
        val fakeError = if (securityFailure) SecurityException("thread denied") else RejectedExecutionException()
        doThrow(fakeError).whenever(mockWorker).execute(any())
        var delivered = 0

        // When
        testedDispatcher.execute { delivered++ }

        // Then
        verify(mockWorker).shutdown()
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            "Unable to start or stop the feature flags callback worker.",
            fakeError
        )
        doAnswer { pending.addLast(it.getArgument(0)); null }.whenever(mockWorker).execute(any())
        testedDispatcher.execute { delivered++ }
        pending.removeFirst().run()
        assertThat(delivered).isEqualTo(2)
    }

    @Test
    fun `M permit later worker creation W execute() {worker creation denied}`() {
        // Given
        var attempts = 0
        var delivered = 0
        val fakeError = SecurityException("worker denied")
        testedDispatcher = EvaluationCallbackDispatcher(mockLogger) {
            if (attempts++ == 0) throw fakeError
            mockWorker
        }

        // When
        testedDispatcher.execute { delivered++ }
        testedDispatcher.execute { delivered++ }
        pending.removeFirst().run()

        // Then
        assertThat(delivered).isEqualTo(2)
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            "Unable to start or stop the feature flags callback worker.",
            fakeError
        )
    }

    @Test
    fun `M preserve serial delivery W execute() {enqueue races with drain exit}`() {
        // Given
        val workers = mutableListOf<ExecutorService>()
        val producer = Executors.newSingleThreadExecutor()
        testedDispatcher = EvaluationCallbackDispatcher(mockLogger) {
            Executors.newSingleThreadExecutor().also { synchronized(workers) { workers.add(it) } }
        }
        val active = AtomicInteger()
        val maximumActive = AtomicInteger()

        try {
            repeat(32) {
                val started = CountDownLatch(1)
                val release = CountDownLatch(1)
                val delivered = CountDownLatch(1)
                testedDispatcher.execute {
                    maximumActive.accumulateAndGet(active.incrementAndGet(), ::maxOf)
                    started.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    active.decrementAndGet()
                }
                check(started.await(5, TimeUnit.SECONDS))

                // When
                val submitted = producer.submit {
                    release.countDown()
                    testedDispatcher.execute {
                        maximumActive.accumulateAndGet(active.incrementAndGet(), ::maxOf)
                        active.decrementAndGet()
                        delivered.countDown()
                    }
                }

                // Then
                submitted.get(5, TimeUnit.SECONDS)
                assertThat(delivered.await(5, TimeUnit.SECONDS)).isTrue()
            }
            assertThat(maximumActive.get()).isEqualTo(1)
        } finally {
            producer.shutdownNow()
            synchronized(workers) { workers.toList() }.forEach {
                assertThat(it.isShutdown).isTrue()
                assertThat(it.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
            }
        }
    }
}
