/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.ResolutionReason
import com.datadog.android.flags.model.UnparsedFlag
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import com.datadog.android.internal.time.TimeProvider
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class EvaluationEventsProcessorTest {

    @Mock
    lateinit var mockWriter: EvaluationEventWriter

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockScheduledExecutor: ScheduledExecutorService

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockScheduledFuture: ScheduledFuture<*>

    @LongForgery(min = 1000000L)
    var fakeTimestamp = 0L

    @StringForgery
    lateinit var fakeFlagName: String

    @StringForgery
    lateinit var fakeTargetingKey: String

    @StringForgery
    lateinit var fakeVariantKey: String

    @StringForgery
    lateinit var fakeAllocationKey: String

    @StringForgery
    lateinit var fakeValue: String

    private lateinit var testedProcessor: EvaluationEventsProcessor
    private lateinit var fakeContext: EvaluationContext
    private lateinit var fakeData: UnparsedFlag

    @BeforeEach
    fun `set up`() {
        testedProcessor = EvaluationEventsProcessor(
            writer = mockWriter,
            timeProvider = mockTimeProvider,
            scheduledExecutor = mockScheduledExecutor,
            internalLogger = mockInternalLogger,
            flushIntervalMs = 10_000L,
            maxAggregations = 1000
        )

        fakeContext = EvaluationContext(targetingKey = fakeTargetingKey)
        fakeData = UnparsedFlag(
            value = fakeValue,
            variationKey = fakeVariantKey,
            allocationKey = fakeAllocationKey,
            reason = ResolutionReason.MATCHED.name
        )

        whenever(mockTimeProvider.getDeviceTimestampMillis()) doReturn fakeTimestamp
    }

    // region processEvaluation

    @Test
    fun `M aggregate evaluation W processEvaluation() { first evaluation }`() {
        // Given
        // When
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, fakeData, null, null)
        testedProcessor.flush()

        // Then
        verify(mockWriter).write(any())
    }

    @Test
    fun `M aggregate same key W processEvaluation() { identical evaluations }`() {
        // Given
        // When
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, fakeData, null, null)
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, fakeData, null, null)
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, fakeData, null, null)
        testedProcessor.flush()

        // Then
        verify(mockWriter, times(1)).write(any())
    }

    @Test
    fun `M create separate aggregations W processEvaluation() { different flags }`(forge: Forge) {
        // Given
        val anotherFlagName = forge.anAlphabeticalString()

        // When
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, fakeData, null, null)
        testedProcessor.processEvaluation(anotherFlagName, fakeContext, fakeData, null, null)
        testedProcessor.flush()

        // Then
        verify(mockWriter, times(2)).write(any())
    }

    @Test
    fun `M create separate aggregations W processEvaluation() { different targeting keys }`(forge: Forge) {
        // Given
        val anotherContext = EvaluationContext(targetingKey = forge.anAlphabeticalString())

        // When
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, fakeData, null, null)
        testedProcessor.processEvaluation(fakeFlagName, anotherContext, fakeData, null, null)
        testedProcessor.flush()

        // Then
        verify(mockWriter, times(2)).write(any())
    }

    @Test
    fun `M create separate aggregations W processEvaluation() { different variants }`(forge: Forge) {
        // Given
        val anotherData = fakeData.copy(variationKey = forge.anAlphabeticalString())

        // When
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, fakeData, null, null)
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, anotherData, null, null)
        testedProcessor.flush()

        // Then
        verify(mockWriter, times(2)).write(any())
    }

    @Test
    fun `M aggregate by error code W processEvaluation() { same code different messages }`(forge: Forge) {
        // Given
        val errorCode = ErrorCode.TYPE_MISMATCH.name
        val errorMessage1 = "Error message 1: ${forge.anAlphabeticalString()}"
        val errorMessage2 = "Error message 2: ${forge.anAlphabeticalString()}"
        val errorMessage3 = "Error message 3: ${forge.anAlphabeticalString()}"

        // When
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, fakeData, errorCode, errorMessage1)
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, fakeData, errorCode, errorMessage2)
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, fakeData, errorCode, errorMessage3)
        testedProcessor.flush()

        // Then - should aggregate into one event
        val eventCaptor = argumentCaptor<com.datadog.android.flags.model.BatchedFlagEvaluations.FlagEvaluation>()
        verify(mockWriter, times(1)).write(eventCaptor.capture())

        // Verify it used the last error message
        val capturedEvent = eventCaptor.firstValue
        assertThat(capturedEvent.error?.message).isEqualTo(errorMessage3)
        assertThat(capturedEvent.evaluationCount).isEqualTo(3L)
    }

    @Test
    fun `M create separate aggregations W processEvaluation() { different error codes }`() {
        // Given
        val errorCode1 = ErrorCode.FLAG_NOT_FOUND.name
        val errorCode2 = ErrorCode.PROVIDER_NOT_READY.name
        val errorMessage1 = "Flag not found"
        val errorMessage2 = "Provider not ready"

        // When
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, fakeData, errorCode1, errorMessage1)
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, fakeData, errorCode2, errorMessage2)
        testedProcessor.flush()

        // Then
        verify(mockWriter, times(2)).write(any())
    }

    @Test
    fun `M increment count W processEvaluation() { multiple evaluations same key }`() {
        // Given
        val timestamps = listOf(fakeTimestamp, fakeTimestamp + 1000, fakeTimestamp + 2000)
        whenever(mockTimeProvider.getDeviceTimestampMillis())
            .thenReturn(timestamps[0], timestamps[1], timestamps[2])

        // When
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, fakeData, null, null)
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, fakeData, null, null)
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, fakeData, null, null)
        testedProcessor.flush()

        // Then
        val eventCaptor = argumentCaptor<com.datadog.android.flags.model.BatchedFlagEvaluations.FlagEvaluation>()
        verify(mockWriter).write(eventCaptor.capture())

        val event = eventCaptor.firstValue
        assertThat(event.evaluationCount).isEqualTo(3L)
        assertThat(event.firstEvaluation).isEqualTo(timestamps[0])
        assertThat(event.lastEvaluation).isEqualTo(timestamps[2])
    }

    // endregion

    // region flush

    @Test
    fun `M write events W flush() { aggregations exist }`() {
        // Given
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, fakeData, null, null)

        // When
        testedProcessor.flush()

        // Then
        verify(mockWriter).write(any())
    }

    @Test
    fun `M not write W flush() { empty aggregations }`() {
        // Given
        // When
        testedProcessor.flush()

        // Then
        verify(mockWriter, never()).write(any())
    }

    @Test
    fun `M clear aggregations W flush() { after flush }`() {
        // Given
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, fakeData, null, null)

        // When
        testedProcessor.flush()
        testedProcessor.flush()

        // Then - second flush should not write anything
        verify(mockWriter, times(1)).write(any())
    }

    @Test
    fun `M flush automatically W processEvaluation() { size limit reached }`() {
        // Given
        val maxAggregations = 5
        val testProcessor = EvaluationEventsProcessor(
            writer = mockWriter,
            timeProvider = mockTimeProvider,
            scheduledExecutor = mockScheduledExecutor,
            internalLogger = mockInternalLogger,
            maxAggregations = maxAggregations
        )

        // When
        repeat(maxAggregations) { index ->
            val uniqueContext = EvaluationContext(targetingKey = "user-$index")
            testProcessor.processEvaluation(fakeFlagName, uniqueContext, fakeData, null, null)
        }

        // Then - should have auto-flushed
        verify(mockWriter, times(maxAggregations)).write(any())
    }

    @Test
    fun `M continue aggregating W flush() { after flush }`() {
        // Given
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, fakeData, null, null)
        testedProcessor.flush()

        // When - new evaluation after flush
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, fakeData, null, null)
        testedProcessor.flush()

        // Then
        verify(mockWriter, times(2)).write(any())
    }

    // endregion

    // region schedulePeriodicFlush

    @Test
    fun `M schedule flush W schedulePeriodicFlush()`() {
        // Given
        whenever(mockScheduledExecutor.schedule(any<Runnable>(), any(), any())) doReturn mockScheduledFuture

        // When
        testedProcessor.schedulePeriodicFlush()

        // Then
        verify(mockScheduledExecutor).schedule(
            any<Runnable>(),
            eq(10_000L),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `M log error W schedulePeriodicFlush() { executor throws }`(forge: Forge) {
        // Given
        val exception = RejectedExecutionException(forge.anAlphabeticalString())
        whenever(mockScheduledExecutor.schedule(any<Runnable>(), any(), any())) doReturn mockScheduledFuture
            .thenThrow(exception)

        // When
        testedProcessor.schedulePeriodicFlush()
        testedProcessor.schedulePeriodicFlush() // Second call throws

        // Then
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            any(),
            any(),
            eq(exception)
        )
    }

    // endregion

    // region stop

    @Test
    fun `M flush and cancel W stop()`() {
        // Given
        whenever(mockScheduledExecutor.schedule(any<Runnable>(), any(), any())) doReturn mockScheduledFuture
        testedProcessor.schedulePeriodicFlush()
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, fakeData, null, null)

        // When
        testedProcessor.stop()

        // Then
        verify(mockScheduledFuture).cancel(false)
        verify(mockWriter).write(any())
    }

    @Test
    fun `M flush W stop() { no scheduled task }`() {
        // Given
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, fakeData, null, null)

        // When
        testedProcessor.stop()

        // Then
        verify(mockWriter).write(any())
    }

    @Test
    fun `M not write W stop() { empty aggregations }`() {
        // Given
        // When
        testedProcessor.stop()

        // Then
        verify(mockWriter, never()).write(any())
    }

    // endregion

    // region Thread Safety

    @Test
    fun `M handle concurrent evaluations W processEvaluation() { same key }`() {
        // Given
        val threadCount = 10
        val executionsPerThread = 100

        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(threadCount)

        // When
        val threads = (1..threadCount).map {
            Thread {
                startLatch.await()
                repeat(executionsPerThread) {
                    testedProcessor.processEvaluation(fakeFlagName, fakeContext, fakeData, null, null)
                }
                finishLatch.countDown()
            }
        }

        threads.forEach { it.start() }
        startLatch.countDown() // Start all at once
        finishLatch.await()

        testedProcessor.flush()

        // Then - should aggregate all into one event
        val eventCaptor = argumentCaptor<com.datadog.android.flags.model.BatchedFlagEvaluations.FlagEvaluation>()
        verify(mockWriter).write(eventCaptor.capture())

        val event = eventCaptor.firstValue
        assertThat(event.evaluationCount).isEqualTo((threadCount * executionsPerThread).toLong())
    }

    @Test
    fun `M handle concurrent evaluations W processEvaluation() { different keys }`() {
        // Given
        val threadCount = 10
        val executionsPerThread = 50

        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(threadCount)

        // When
        val threads = (1..threadCount).map { threadIndex ->
            Thread {
                startLatch.await()
                repeat(executionsPerThread) { executionIndex ->
                    val uniqueContext = EvaluationContext(
                        targetingKey = "thread-$threadIndex-execution-$executionIndex"
                    )
                    testedProcessor.processEvaluation(fakeFlagName, uniqueContext, fakeData, null, null)
                }
                finishLatch.countDown()
            }
        }

        threads.forEach { it.start() }
        startLatch.countDown()
        finishLatch.await()

        testedProcessor.flush()

        // Then
        verify(mockWriter, times(threadCount * executionsPerThread)).write(any())
    }

    @Test
    fun `M handle concurrent flush W processEvaluation and flush() { mixed operations }`() {
        // Given
        val processingThreadCount = 5
        val flushThreadCount = 2
        val executionsPerThread = 100

        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(processingThreadCount + flushThreadCount)

        // When
        val processingThreads = (1..processingThreadCount).map { threadIndex ->
            Thread {
                startLatch.await()
                repeat(executionsPerThread) { executionIndex ->
                    val context = EvaluationContext(targetingKey = "user-$threadIndex-$executionIndex")
                    testedProcessor.processEvaluation(fakeFlagName, context, fakeData, null, null)
                }
                finishLatch.countDown()
            }
        }

        val flushThreads = (1..flushThreadCount).map {
            Thread {
                startLatch.await()
                repeat(10) {
                    Thread.sleep(5)
                    testedProcessor.flush()
                }
                finishLatch.countDown()
            }
        }

        val allThreads = processingThreads + flushThreads
        allThreads.forEach { it.start() }
        startLatch.countDown()
        finishLatch.await()

        testedProcessor.flush() // Final flush

        // Then - should have written some events
        verify(mockWriter, times(processingThreadCount * executionsPerThread)).write(any())
    }

    @Test
    fun `M maintain consistency W processEvaluation() { high contention }`() {
        // Given
        val threadCount = 20
        val executionsPerThread = 200

        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(threadCount)

        // When
        val threads = (1..threadCount).map {
            Thread {
                startLatch.await()
                repeat(executionsPerThread) {
                    testedProcessor.processEvaluation(fakeFlagName, fakeContext, fakeData, null, null)
                }
                finishLatch.countDown()
            }
        }

        threads.forEach { it.start() }
        startLatch.countDown()
        finishLatch.await()

        testedProcessor.flush()

        // Then
        val eventCaptor = argumentCaptor<com.datadog.android.flags.model.BatchedFlagEvaluations.FlagEvaluation>()
        verify(mockWriter).write(eventCaptor.capture())

        val event = eventCaptor.firstValue
        assertThat(event.evaluationCount).isEqualTo((threadCount * executionsPerThread).toLong())
    }

    // endregion
}
