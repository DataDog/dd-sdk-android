/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagEvaluation
import com.datadog.android.flags.model.ResolutionReason
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
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
    lateinit var fakeFlagKey: String

    @StringForgery
    lateinit var fakeTargetingKey: String

    @StringForgery
    lateinit var fakeVariantKey: String

    @StringForgery
    lateinit var fakeAllocationKey: String

    private lateinit var testedProcessor: EvaluationEventsProcessor
    private lateinit var fakeContext: EvaluationContext
    private lateinit var fakeService: String
    private lateinit var fakeApplicationId: String
    private lateinit var fakeViewName: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeService = forge.anAlphabeticalString()
        fakeApplicationId = forge.anAlphabeticalString()
        fakeViewName = forge.anAlphabeticalString()
        fakeContext = EvaluationContext(targetingKey = fakeTargetingKey)

        testedProcessor = EvaluationEventsProcessor(
            writer = mockWriter,
            timeProvider = mockTimeProvider,
            scheduledExecutor = mockScheduledExecutor,
            internalLogger = mockInternalLogger,
            flushIntervalMs = TEST_FLUSH_INTERVAL_MS,
            maxAggregations = TEST_MAX_AGGREGATIONS
        )

        lenient().whenever(mockTimeProvider.getDeviceTimestampMillis()) doReturn fakeTimestamp
    }

    // region processEvaluation

    @Test
    fun `M write event W processEvaluation() then flush()`() {
        processEval()
        testedProcessor.flush()

        val events = captureWrittenEvents()
        assertThat(events).hasSize(1)
        assertThat(events.first().flag.key).isEqualTo(fakeFlagKey)
    }

    @Test
    fun `M trigger auto-flush W processEvaluation() { max aggregations reached }`() {
        val maxAggregations = 5
        val processor = EvaluationEventsProcessor(
            writer = mockWriter,
            timeProvider = mockTimeProvider,
            scheduledExecutor = mockScheduledExecutor,
            internalLogger = mockInternalLogger,
            flushIntervalMs = TEST_FLUSH_INTERVAL_MS,
            maxAggregations = maxAggregations
        )

        repeat(maxAggregations) { index ->
            processor.processEvaluation(
                fakeFlagKey,
                EvaluationContext(targetingKey = "user-$index"),
                fakeService, fakeApplicationId, fakeViewName,
                fakeVariantKey, fakeAllocationKey, ResolutionReason.TARGETING_MATCH.name,
                null, null
            )
        }

        val events = captureWrittenEvents()
        assertThat(events).hasSize(maxAggregations)
    }

    // endregion

    // region flush

    @Test
    fun `M not write W flush() { empty aggregations }`() {
        testedProcessor.flush()

        verify(mockWriter, never()).writeAll(any())
    }

    @Test
    fun `M clear and accept new W flush() { after previous flush }`() {
        processEval()
        testedProcessor.flush()

        processEval()
        testedProcessor.flush()

        val captor = argumentCaptor<List<FlagEvaluation>>()
        verify(mockWriter, times(2)).writeAll(captor.capture())
        assertThat(captor.allValues.flatten()).hasSize(2)
    }

    @Test
    fun `M cancel scheduled future W flush()`() {
        whenever(mockScheduledExecutor.schedule(any<Runnable>(), any(), any())) doReturn mockScheduledFuture
        testedProcessor.schedulePeriodicFlush()

        processEval()
        testedProcessor.flush()

        verify(mockScheduledFuture).cancel(false)
    }

    @Test
    fun `M reschedule W flush() { periodic flush enabled }`() {
        var scheduleCount = 0
        whenever(mockScheduledExecutor.schedule(any<Runnable>(), any(), any())).thenAnswer {
            scheduleCount++
            mockScheduledFuture
        }

        testedProcessor.schedulePeriodicFlush()
        val initialCount = scheduleCount

        processEval()
        testedProcessor.flush()

        assertThat(scheduleCount).isGreaterThan(initialCount)
    }

    // endregion

    // region schedulePeriodicFlush

    @Test
    fun `M schedule flush W schedulePeriodicFlush()`() {
        whenever(mockScheduledExecutor.schedule(any<Runnable>(), any(), any())) doReturn mockScheduledFuture

        testedProcessor.schedulePeriodicFlush()

        verify(mockScheduledExecutor).schedule(any<Runnable>(), eq(TEST_FLUSH_INTERVAL_MS), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M log warning W schedulePeriodicFlush() { executor rejects }`(forge: Forge) {
        val exception = RejectedExecutionException(forge.anAlphabeticalString())
        whenever(mockScheduledExecutor.schedule(any<Runnable>(), any(), any()))
            .doReturn(mockScheduledFuture)
            .doThrow(exception)

        testedProcessor.schedulePeriodicFlush()
        testedProcessor.schedulePeriodicFlush()

        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            any<List<InternalLogger.Target>>(),
            any(),
            eq(exception),
            eq(false),
            eq(null)
        )
    }

    @Test
    fun `M reschedule W scheduled task executes()`() {
        var taskRunnable: Runnable? = null
        var scheduleCount = 0
        whenever(mockScheduledExecutor.schedule(any<Runnable>(), any(), any())).thenAnswer {
            scheduleCount++
            taskRunnable = it.getArgument(0)
            mockScheduledFuture
        }

        testedProcessor.schedulePeriodicFlush()
        assertThat(scheduleCount).isEqualTo(1)

        checkNotNull(taskRunnable).run()

        assertThat(scheduleCount).isEqualTo(2)
    }

    @Test
    fun `M reschedule W scheduled task executes() { empty flush }`() {
        var taskRunnable: Runnable? = null
        whenever(mockScheduledExecutor.schedule(any<Runnable>(), any(), any())).thenAnswer {
            taskRunnable = it.getArgument(0)
            mockScheduledFuture
        }

        testedProcessor.schedulePeriodicFlush()
        checkNotNull(taskRunnable).run()

        verify(mockWriter, never()).writeAll(any())
        verify(
            mockScheduledExecutor,
            times(2)
        ).schedule(any<Runnable>(), eq(TEST_FLUSH_INTERVAL_MS), eq(TimeUnit.MILLISECONDS))
    }

    // endregion

    // region Constructor with periodicFlushEnabled

    @Test
    fun `M auto-schedule W constructor { periodicFlushEnabled = true }`() {
        whenever(mockScheduledExecutor.schedule(any<Runnable>(), any(), any())) doReturn mockScheduledFuture

        EvaluationEventsProcessor(
            writer = mockWriter,
            timeProvider = mockTimeProvider,
            scheduledExecutor = mockScheduledExecutor,
            internalLogger = mockInternalLogger,
            flushIntervalMs = TEST_FLUSH_INTERVAL_MS,
            maxAggregations = TEST_MAX_AGGREGATIONS,
            periodicFlushEnabled = true
        )

        verify(mockScheduledExecutor).schedule(any<Runnable>(), eq(TEST_FLUSH_INTERVAL_MS), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M not schedule W constructor { periodicFlushEnabled = false (default) }`() {
        EvaluationEventsProcessor(
            writer = mockWriter,
            timeProvider = mockTimeProvider,
            scheduledExecutor = mockScheduledExecutor,
            internalLogger = mockInternalLogger,
            flushIntervalMs = TEST_FLUSH_INTERVAL_MS,
            maxAggregations = TEST_MAX_AGGREGATIONS
        )

        verify(mockScheduledExecutor, never()).schedule(any<Runnable>(), any(), any())
    }

    // endregion

    // region stop

    @Test
    fun `M flush remaining W stop()`() {
        whenever(mockScheduledExecutor.awaitTermination(any(), any())) doReturn true

        processEval()
        testedProcessor.stop()

        verify(mockScheduledExecutor).shutdown()
        val events = captureWrittenEvents()
        assertThat(events.first().flag.key).isEqualTo(fakeFlagKey)
    }

    @Test
    fun `M not write W stop() { empty aggregations }`() {
        whenever(mockScheduledExecutor.awaitTermination(any(), any())) doReturn true

        testedProcessor.stop()

        verify(mockScheduledExecutor).shutdown()
        verify(mockWriter, never()).writeAll(any())
    }

    @Test
    fun `M shutdown before cancel W stop()`() {
        whenever(mockScheduledExecutor.schedule(any<Runnable>(), any(), any()))
            .doReturn(mockScheduledFuture)
            .doThrow(RejectedExecutionException("Executor shutdown"))
        whenever(mockScheduledExecutor.awaitTermination(any(), any())) doReturn true
        testedProcessor.schedulePeriodicFlush()

        testedProcessor.stop()

        val inOrder = inOrder(mockScheduledExecutor, mockScheduledFuture)
        inOrder.verify(mockScheduledExecutor).shutdown()
        inOrder.verify(mockScheduledFuture, atLeastOnce()).cancel(false)
    }

    @Test
    fun `M force shutdown W stop() { termination timeout }`() {
        whenever(mockScheduledExecutor.awaitTermination(any(), any())) doReturn false
        whenever(mockScheduledExecutor.shutdownNow()).thenReturn(emptyList())

        testedProcessor.stop()

        verify(mockScheduledExecutor).shutdown()
        verify(mockScheduledExecutor).shutdownNow()
    }

    @Test
    fun `M force shutdown and restore interrupt W stop() { interrupted }`() {
        whenever(mockScheduledExecutor.awaitTermination(any(), any())).thenThrow(InterruptedException())
        whenever(mockScheduledExecutor.shutdownNow()).thenReturn(emptyList())

        testedProcessor.stop()

        verify(mockScheduledExecutor).shutdown()
        verify(mockScheduledExecutor).shutdownNow()
        assertThat(Thread.interrupted()).isTrue()
    }

    @Test
    fun `M prevent reschedule W stop() { task runs after stop }`() {
        var taskRunnable: Runnable? = null
        var scheduleCount = 0
        whenever(mockScheduledExecutor.schedule(any<Runnable>(), any(), any())).thenAnswer {
            scheduleCount++
            taskRunnable = it.getArgument(0)
            mockScheduledFuture
        }
        whenever(mockScheduledExecutor.awaitTermination(any(), any())) doReturn true

        testedProcessor.schedulePeriodicFlush()
        val initialCount = scheduleCount

        testedProcessor.stop()
        checkNotNull(taskRunnable).run()

        assertThat(scheduleCount).isEqualTo(initialCount)
    }

    // endregion

    // region Thread Safety

    @Test
    fun `M aggregate all W processEvaluation() { concurrent same key }`() {
        val threadCount = 10
        val executionsPerThread = 100

        runConcurrently(threadCount) {
            repeat(executionsPerThread) { processEval() }
        }

        testedProcessor.flush()

        val events = captureWrittenEvents()
        assertThat(events).hasSize(1)
        assertThat(events.first().evaluationCount).isEqualTo((threadCount * executionsPerThread).toLong())
    }

    @Test
    fun `M write all W processEvaluation() { concurrent different keys }`() {
        val threadCount = 10
        val executionsPerThread = 50

        runConcurrently(threadCount) { threadIndex ->
            repeat(executionsPerThread) { execIndex ->
                testedProcessor.processEvaluation(
                    fakeFlagKey,
                    EvaluationContext(targetingKey = "thread-$threadIndex-exec-$execIndex"),
                    fakeService, fakeApplicationId, fakeViewName,
                    fakeVariantKey, fakeAllocationKey, ResolutionReason.TARGETING_MATCH.name,
                    null, null
                )
            }
        }

        testedProcessor.flush()

        val events = captureWrittenEvents()
        assertThat(events).hasSize(threadCount * executionsPerThread)
    }

    @Test
    fun `M write all W flush() { concurrent with processEvaluation }`() {
        val processingThreads = 5
        val flushThreads = 2
        val executionsPerThread = 100

        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(processingThreads + flushThreads)

        val processingWorkers = (1..processingThreads).map { threadIndex ->
            Thread {
                startLatch.await()
                repeat(executionsPerThread) { execIndex ->
                    testedProcessor.processEvaluation(
                        fakeFlagKey,
                        EvaluationContext(targetingKey = "user-$threadIndex-$execIndex"),
                        fakeService, fakeApplicationId, fakeViewName,
                        fakeVariantKey, fakeAllocationKey, ResolutionReason.TARGETING_MATCH.name,
                        null, null
                    )
                }
                finishLatch.countDown()
            }
        }

        val flushWorkers = (1..flushThreads).map {
            Thread {
                startLatch.await()
                repeat(10) {
                    Thread.sleep(5)
                    testedProcessor.flush()
                }
                finishLatch.countDown()
            }
        }

        (processingWorkers + flushWorkers).forEach { it.start() }
        startLatch.countDown()
        finishLatch.await()
        testedProcessor.flush()

        val captor = argumentCaptor<List<FlagEvaluation>>()
        verify(mockWriter, atLeast(1)).writeAll(captor.capture())
        assertThat(captor.allValues.sumOf { it.size }).isEqualTo(processingThreads * executionsPerThread)
    }

    @Test
    fun `M skip concurrent flush W flush() { already in progress }`() {
        val writeStarted = CountDownLatch(1)
        val blockWrite = CountDownLatch(1)
        var writeCount = 0

        val slowWriter = object : EvaluationEventWriter {
            override fun writeAll(events: List<FlagEvaluation>) {
                writeCount++
                writeStarted.countDown()
                blockWrite.await()
            }
        }

        val processor = EvaluationEventsProcessor(
            writer = slowWriter,
            timeProvider = mockTimeProvider,
            scheduledExecutor = mockScheduledExecutor,
            internalLogger = mockInternalLogger,
            flushIntervalMs = TEST_FLUSH_INTERVAL_MS,
            maxAggregations = TEST_MAX_AGGREGATIONS
        )

        processor.processEvaluation(
            fakeFlagKey, fakeContext, fakeService, fakeApplicationId, fakeViewName,
            fakeVariantKey, fakeAllocationKey, ResolutionReason.TARGETING_MATCH.name, null, null
        )

        val flushThread = Thread { processor.flush() }
        flushThread.start()
        writeStarted.await()

        processor.flush()
        processor.flush()
        processor.flush()

        blockWrite.countDown()
        flushThread.join()

        assertThat(writeCount).isEqualTo(1)
    }

    @Test
    fun `M not lose events W stop() { flush in progress }`() {
        val writeStarted = CountDownLatch(1)
        val continueWrite = CountDownLatch(1)
        val writtenEvents = CopyOnWriteArrayList<FlagEvaluation>()

        val slowWriter = object : EvaluationEventWriter {
            override fun writeAll(events: List<FlagEvaluation>) {
                if (writtenEvents.isEmpty()) {
                    writeStarted.countDown()
                    continueWrite.await()
                }
                writtenEvents.addAll(events)
            }
        }

        val processor = EvaluationEventsProcessor(
            writer = slowWriter,
            timeProvider = mockTimeProvider,
            scheduledExecutor = mockScheduledExecutor,
            internalLogger = mockInternalLogger,
            flushIntervalMs = TEST_FLUSH_INTERVAL_MS,
            maxAggregations = TEST_MAX_AGGREGATIONS
        )

        whenever(mockScheduledExecutor.awaitTermination(any(), any())) doReturn true

        repeat(10) { index ->
            processor.processEvaluation(
                fakeFlagKey, EvaluationContext(targetingKey = "initial-$index"),
                fakeService, fakeApplicationId, fakeViewName,
                fakeVariantKey, fakeAllocationKey, ResolutionReason.TARGETING_MATCH.name, null, null
            )
        }

        val flushThread = Thread { processor.flush() }
        flushThread.start()
        writeStarted.await()

        repeat(5) { index ->
            processor.processEvaluation(
                fakeFlagKey, EvaluationContext(targetingKey = "during-flush-$index"),
                fakeService, fakeApplicationId, fakeViewName,
                fakeVariantKey, fakeAllocationKey, ResolutionReason.TARGETING_MATCH.name, null, null
            )
        }

        val stopThread = Thread {
            continueWrite.countDown()
            processor.stop()
        }
        stopThread.start()
        stopThread.join(5000)
        flushThread.join(1000)

        assertThat(writtenEvents).hasSize(15)
    }

    @Test
    fun `M not lose evaluations W processEvaluation() { concurrent with flush swap }`() {
        val totalWritten = AtomicInteger(0)

        val trackingWriter = object : EvaluationEventWriter {
            override fun writeAll(events: List<FlagEvaluation>) {
                totalWritten.addAndGet(events.sumOf { it.evaluationCount.toInt() })
            }
        }

        val processor = EvaluationEventsProcessor(
            writer = trackingWriter,
            timeProvider = mockTimeProvider,
            scheduledExecutor = mockScheduledExecutor,
            internalLogger = mockInternalLogger,
            flushIntervalMs = TEST_FLUSH_INTERVAL_MS,
            maxAggregations = Int.MAX_VALUE
        )

        whenever(mockScheduledExecutor.awaitTermination(any(), any())) doReturn true

        val threadCount = 10
        val evaluationsPerThread = 100
        val flushCount = 20
        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(threadCount + 1)

        val evalThreads = (1..threadCount).map {
            Thread {
                startLatch.await()
                repeat(evaluationsPerThread) {
                    processor.processEvaluation(
                        fakeFlagKey, fakeContext, fakeService, fakeApplicationId, fakeViewName,
                        fakeVariantKey, fakeAllocationKey, ResolutionReason.TARGETING_MATCH.name, null, null
                    )
                }
                finishLatch.countDown()
            }
        }

        val flushThread = Thread {
            startLatch.await()
            repeat(flushCount) {
                Thread.sleep(1)
                processor.flush()
            }
            finishLatch.countDown()
        }

        evalThreads.forEach { it.start() }
        flushThread.start()
        startLatch.countDown()
        finishLatch.await()
        processor.stop()

        assertThat(totalWritten.get()).isEqualTo(threadCount * evaluationsPerThread)
    }

    @Test
    fun `M accept evaluations W flush() { during flush operation }`() {
        whenever(mockScheduledExecutor.schedule(any<Runnable>(), any(), any())) doReturn mockScheduledFuture

        val flushInProgress = CountDownLatch(1)
        val continueFlush = CountDownLatch(1)
        val writeCount = AtomicInteger(0)
        val firstCall = AtomicBoolean(true)

        val slowWriter = object : EvaluationEventWriter {
            override fun writeAll(events: List<FlagEvaluation>) {
                val isFirst = firstCall.compareAndSet(true, false)
                writeCount.addAndGet(events.size)
                if (isFirst) {
                    flushInProgress.countDown()
                    continueFlush.await()
                }
            }
        }

        val processor = EvaluationEventsProcessor(
            writer = slowWriter,
            timeProvider = mockTimeProvider,
            scheduledExecutor = mockScheduledExecutor,
            internalLogger = mockInternalLogger,
            flushIntervalMs = TEST_FLUSH_INTERVAL_MS,
            maxAggregations = TEST_MAX_AGGREGATIONS
        )

        repeat(50) { index ->
            processor.processEvaluation(
                fakeFlagKey, EvaluationContext(targetingKey = "initial-$index"),
                fakeService, fakeApplicationId, fakeViewName,
                fakeVariantKey, fakeAllocationKey, ResolutionReason.TARGETING_MATCH.name, null, null
            )
        }

        val flushComplete = CountDownLatch(1)
        val flushThread = Thread {
            processor.flush()
            flushComplete.countDown()
        }
        flushThread.start()
        flushInProgress.await()

        repeat(25) { index ->
            processor.processEvaluation(
                fakeFlagKey, EvaluationContext(targetingKey = "during-flush-$index"),
                fakeService, fakeApplicationId, fakeViewName,
                fakeVariantKey, fakeAllocationKey, ResolutionReason.TARGETING_MATCH.name, null, null
            )
        }

        continueFlush.countDown()
        flushComplete.await()
        flushThread.join()
        processor.flush()

        assertThat(writeCount.get()).isEqualTo(75)
    }

    // endregion

    // region Helpers

    private fun processEval(
        flagKey: String = fakeFlagKey,
        context: EvaluationContext = fakeContext,
        variantKey: String? = fakeVariantKey,
        allocationKey: String? = fakeAllocationKey,
        reason: String? = ResolutionReason.TARGETING_MATCH.name,
        errorCode: String? = null,
        errorMessage: String? = null
    ) {
        testedProcessor.processEvaluation(
            flagKey, context, fakeService, fakeApplicationId, fakeViewName,
            variantKey, allocationKey, reason, errorCode, errorMessage
        )
    }

    private fun captureWrittenEvents(): List<FlagEvaluation> {
        val captor = argumentCaptor<List<FlagEvaluation>>()
        verify(mockWriter).writeAll(captor.capture())
        return captor.firstValue
    }

    private fun runConcurrently(threadCount: Int, action: (Int) -> Unit) {
        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(threadCount)

        val threads = (1..threadCount).map { index ->
            Thread {
                startLatch.await()
                action(index)
                finishLatch.countDown()
            }
        }

        threads.forEach { it.start() }
        startLatch.countDown()
        finishLatch.await()
    }

    // endregion

    companion object {
        private const val TEST_FLUSH_INTERVAL_MS = 10_000L
        private const val TEST_MAX_AGGREGATIONS = 1000
    }
}
