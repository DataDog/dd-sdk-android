/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.internal.aggregation.EvaluationAggregationStats
import com.datadog.android.flags.internal.aggregation.EvaluationAggregator
import com.datadog.android.flags.model.EvaluationContext
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
import java.util.concurrent.atomic.AtomicLong

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
    private lateinit var testedAggregator: EvaluationAggregator
    private lateinit var fakeContext: EvaluationContext
    private lateinit var fakeApplicationId: String
    private lateinit var fakeViewName: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeApplicationId = forge.anAlphabeticalString()
        fakeViewName = forge.anAlphabeticalString()
        fakeContext = EvaluationContext(targetingKey = fakeTargetingKey)
        testedAggregator = EvaluationAggregator(TEST_MAX_AGGREGATIONS)

        testedProcessor = EvaluationEventsProcessor(
            writer = mockWriter,
            timeProvider = mockTimeProvider,
            scheduledExecutor = mockScheduledExecutor,
            internalLogger = mockInternalLogger,
            flushIntervalMs = TEST_FLUSH_INTERVAL_MS,
            aggregator = testedAggregator,
            periodicFlushEnabled = false
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
        assertThat(events.first().aggregationKey.flagKey).isEqualTo(fakeFlagKey)
    }

    @Test
    fun `M trigger auto-flush W processEvaluation() { max aggregations reached }`() {
        val maxAggregations = 5
        val aggregator = EvaluationAggregator(maxAggregations)
        val processor = EvaluationEventsProcessor(
            writer = mockWriter,
            timeProvider = mockTimeProvider,
            scheduledExecutor = mockScheduledExecutor,
            internalLogger = mockInternalLogger,
            flushIntervalMs = TEST_FLUSH_INTERVAL_MS,
            aggregator = aggregator
        )

        repeat(maxAggregations) { index ->
            processor.processEvaluation(
                fakeFlagKey,
                EvaluationContext(targetingKey = "user-$index"),
                fakeApplicationId,
                fakeViewName,
                fakeVariantKey,
                fakeAllocationKey,
                null,
                null
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

        val captor = argumentCaptor<List<EvaluationAggregationStats>>()
        verify(mockWriter, times(2)).writeAll(captor.capture())
        assertThat(captor.allValues.flatten()).hasSize(2)
    }

    // endregion

    // region periodic flush

    @Test
    fun `M schedule periodic flush W constructor()`() {
        whenever(
            mockScheduledExecutor.scheduleAtFixedRate(any<Runnable>(), any(), any(), any())
        ) doReturn mockScheduledFuture

        createSchedulingEnabledProcessor()

        verify(mockScheduledExecutor).scheduleAtFixedRate(
            any<Runnable>(),
            eq(TEST_FLUSH_INTERVAL_MS),
            eq(TEST_FLUSH_INTERVAL_MS),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `M log warning W startPeriodicFlush() { executor rejects }`(forge: Forge) {
        val exception = RejectedExecutionException(forge.anAlphabeticalString())
        whenever(mockScheduledExecutor.scheduleAtFixedRate(any<Runnable>(), any(), any(), any()))
            .doThrow(exception)

        createSchedulingEnabledProcessor()

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
    fun `M flush W periodic task executes { interval elapsed }`() {
        var taskRunnable: Runnable? = null
        whenever(
            mockScheduledExecutor.scheduleAtFixedRate(any<Runnable>(), any(), any(), any())
        ).thenAnswer {
            taskRunnable = it.getArgument(0)
            mockScheduledFuture
        }
        whenever(mockTimeProvider.getDeviceTimestampMillis()) doReturn fakeTimestamp

        val processor = createSchedulingEnabledProcessor()
        processor.processEvaluation(
            fakeFlagKey,
            fakeContext,
            fakeApplicationId,
            fakeViewName,
            fakeVariantKey,
            fakeAllocationKey,
            null,
            null
        )
        processor.flush()

        // Simulate time passing
        whenever(mockTimeProvider.getDeviceTimestampMillis()) doReturn (fakeTimestamp + TEST_FLUSH_INTERVAL_MS)
        processor.processEvaluation(
            fakeFlagKey,
            fakeContext,
            fakeApplicationId,
            fakeViewName,
            fakeVariantKey,
            fakeAllocationKey,
            null,
            null
        )

        // Run periodic task - should flush since interval has elapsed
        checkNotNull(taskRunnable).run()

        val captor = argumentCaptor<List<EvaluationAggregationStats>>()
        verify(mockWriter, times(2)).writeAll(captor.capture())
    }

    @Test
    fun `M skip flush W periodic task executes { interval not elapsed }`() {
        var taskRunnable: Runnable? = null
        whenever(
            mockScheduledExecutor.scheduleAtFixedRate(any<Runnable>(), any(), any(), any())
        ).thenAnswer {
            taskRunnable = it.getArgument(0)
            mockScheduledFuture
        }
        whenever(mockTimeProvider.getDeviceTimestampMillis()) doReturn fakeTimestamp

        val processor = createSchedulingEnabledProcessor()
        processor.processEvaluation(
            fakeFlagKey,
            fakeContext,
            fakeApplicationId,
            fakeViewName,
            fakeVariantKey,
            fakeAllocationKey,
            null,
            null
        )
        processor.flush()

        // Simulate time passing but less than interval
        whenever(mockTimeProvider.getDeviceTimestampMillis()) doReturn (fakeTimestamp + TEST_FLUSH_INTERVAL_MS - 1)
        processor.processEvaluation(
            fakeFlagKey,
            fakeContext,
            fakeApplicationId,
            fakeViewName,
            fakeVariantKey,
            fakeAllocationKey,
            null,
            null
        )

        // Run periodic task - should NOT flush since interval hasn't elapsed
        checkNotNull(taskRunnable).run()

        // Only one writeAll from the explicit flush()
        verify(mockWriter, times(1)).writeAll(any())
    }

    @Test
    fun `M flush W periodic task executes { no previous flush }`() {
        var taskRunnable: Runnable? = null
        whenever(
            mockScheduledExecutor.scheduleAtFixedRate(any<Runnable>(), any(), any(), any())
        ).thenAnswer {
            taskRunnable = it.getArgument(0)
            mockScheduledFuture
        }
        // Set time to be greater than flush interval (simulating first run after startup)
        whenever(mockTimeProvider.getDeviceTimestampMillis()) doReturn TEST_FLUSH_INTERVAL_MS

        val processor = createSchedulingEnabledProcessor()
        processor.processEvaluation(
            fakeFlagKey,
            fakeContext,
            fakeApplicationId,
            fakeViewName,
            fakeVariantKey,
            fakeAllocationKey,
            null,
            null
        )

        // Run periodic task - should flush since lastFlushTimeMs is 0
        checkNotNull(taskRunnable).run()

        verify(mockWriter).writeAll(any())
    }

    @Test
    fun `M not write W periodic task executes { empty aggregations }`() {
        var taskRunnable: Runnable? = null
        whenever(
            mockScheduledExecutor.scheduleAtFixedRate(any<Runnable>(), any(), any(), any())
        ).thenAnswer {
            taskRunnable = it.getArgument(0)
            mockScheduledFuture
        }
        whenever(mockTimeProvider.getDeviceTimestampMillis()) doReturn TEST_FLUSH_INTERVAL_MS

        createSchedulingEnabledProcessor()
        checkNotNull(taskRunnable).run()

        verify(mockWriter, never()).writeAll(any())
    }

    // endregion

    // region Constructor with periodicFlushEnabled

    @Test
    fun `M auto-schedule W constructor { periodicFlushEnabled = true (default) }`() {
        whenever(
            mockScheduledExecutor.scheduleAtFixedRate(any<Runnable>(), any(), any(), any())
        ) doReturn mockScheduledFuture

        EvaluationEventsProcessor(
            writer = mockWriter,
            timeProvider = mockTimeProvider,
            scheduledExecutor = mockScheduledExecutor,
            internalLogger = mockInternalLogger,
            flushIntervalMs = TEST_FLUSH_INTERVAL_MS,
            aggregator = testedAggregator
        )

        verify(mockScheduledExecutor).scheduleAtFixedRate(
            any<Runnable>(),
            eq(TEST_FLUSH_INTERVAL_MS),
            eq(TEST_FLUSH_INTERVAL_MS),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `M not schedule W constructor { periodicFlushEnabled = false }`() {
        EvaluationEventsProcessor(
            writer = mockWriter,
            timeProvider = mockTimeProvider,
            scheduledExecutor = mockScheduledExecutor,
            internalLogger = mockInternalLogger,
            flushIntervalMs = TEST_FLUSH_INTERVAL_MS,
            aggregator = testedAggregator,
            periodicFlushEnabled = false
        )

        verify(mockScheduledExecutor, never()).scheduleAtFixedRate(any<Runnable>(), any(), any(), any())
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
        assertThat(events.first().aggregationKey.flagKey).isEqualTo(fakeFlagKey)
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
        whenever(mockScheduledExecutor.scheduleAtFixedRate(any<Runnable>(), any(), any(), any()))
            .doReturn(mockScheduledFuture)
        whenever(mockScheduledExecutor.awaitTermination(any(), any())) doReturn true
        val processor = createSchedulingEnabledProcessor()

        processor.stop()

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
    fun `M cancel scheduled task W stop()`() {
        whenever(mockScheduledExecutor.scheduleAtFixedRate(any<Runnable>(), any(), any(), any()))
            .doReturn(mockScheduledFuture)
        whenever(mockScheduledExecutor.awaitTermination(any(), any())) doReturn true

        val processor = createSchedulingEnabledProcessor()
        processor.stop()

        verify(mockScheduledFuture).cancel(false)
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
        assertThat(events.first().count).isEqualTo((threadCount * executionsPerThread).toLong())
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
                    fakeApplicationId,
                    fakeViewName,
                    fakeVariantKey,
                    fakeAllocationKey,
                    null,
                    null
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
                        fakeApplicationId,
                        fakeViewName,
                        fakeVariantKey,
                        fakeAllocationKey,
                        null,
                        null
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

        val captor = argumentCaptor<List<EvaluationAggregationStats>>()
        verify(mockWriter, atLeast(1)).writeAll(captor.capture())
        assertThat(captor.allValues.sumOf { it.size }).isEqualTo(processingThreads * executionsPerThread)
    }

    @Test
    fun `M skip concurrent flush W flush() { already in progress }`() {
        val writeStarted = CountDownLatch(1)
        val blockWrite = CountDownLatch(1)
        var writeCount = 0

        val slowWriter = object : EvaluationEventWriter {
            override fun writeAll(events: List<EvaluationAggregationStats>) {
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
            aggregator = testedAggregator
        )

        processor.processEvaluation(
            fakeFlagKey,
            fakeContext,
            fakeApplicationId,
            fakeViewName,
            fakeVariantKey,
            fakeAllocationKey,
            null,
            null
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
        val writtenEvents = CopyOnWriteArrayList<EvaluationAggregationStats>()

        val slowWriter = object : EvaluationEventWriter {
            override fun writeAll(events: List<EvaluationAggregationStats>) {
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
            aggregator = testedAggregator
        )

        whenever(mockScheduledExecutor.awaitTermination(any(), any())) doReturn true

        repeat(10) { index ->
            processor.processEvaluation(
                fakeFlagKey,
                EvaluationContext(targetingKey = "initial-$index"),
                fakeApplicationId,
                fakeViewName,
                fakeVariantKey,
                fakeAllocationKey,
                null,
                null
            )
        }

        val flushThread = Thread { processor.flush() }
        flushThread.start()
        writeStarted.await()

        repeat(5) { index ->
            processor.processEvaluation(
                fakeFlagKey,
                EvaluationContext(targetingKey = "during-flush-$index"),
                fakeApplicationId,
                fakeViewName,
                fakeVariantKey,
                fakeAllocationKey,
                null,
                null
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
            override fun writeAll(events: List<EvaluationAggregationStats>) {
                totalWritten.addAndGet(events.sumOf { it.count })
            }
        }

        val aggregator = EvaluationAggregator(Int.MAX_VALUE)
        val processor = EvaluationEventsProcessor(
            writer = trackingWriter,
            timeProvider = mockTimeProvider,
            scheduledExecutor = mockScheduledExecutor,
            internalLogger = mockInternalLogger,
            flushIntervalMs = TEST_FLUSH_INTERVAL_MS,
            aggregator = aggregator
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
                        fakeFlagKey,
                        fakeContext,
                        fakeApplicationId,
                        fakeViewName,
                        fakeVariantKey,
                        fakeAllocationKey,
                        null,
                        null
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
        whenever(
            mockScheduledExecutor.scheduleAtFixedRate(any<Runnable>(), any(), any(), any())
        ) doReturn mockScheduledFuture

        val flushInProgress = CountDownLatch(1)
        val continueFlush = CountDownLatch(1)
        val writeCount = AtomicInteger(0)
        val firstCall = AtomicBoolean(true)

        val slowWriter = object : EvaluationEventWriter {
            override fun writeAll(events: List<EvaluationAggregationStats>) {
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
            aggregator = testedAggregator
        )

        repeat(50) { index ->
            processor.processEvaluation(
                fakeFlagKey,
                EvaluationContext(targetingKey = "initial-$index"),
                fakeApplicationId,
                fakeViewName,
                fakeVariantKey,
                fakeAllocationKey,
                null,
                null
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
                fakeFlagKey,
                EvaluationContext(targetingKey = "during-flush-$index"),
                fakeApplicationId,
                fakeViewName,
                fakeVariantKey,
                fakeAllocationKey,
                null,
                null
            )
        }

        continueFlush.countDown()
        flushComplete.await()
        flushThread.join()
        processor.flush()

        assertThat(writeCount.get()).isEqualTo(75)
    }

    @Test
    fun `M correctly track flush time W periodicFlushTask() { concurrent with manual flush }`() {
        val writeCount = AtomicInteger(0)
        val trackingWriter = object : EvaluationEventWriter {
            override fun writeAll(events: List<EvaluationAggregationStats>) {
                writeCount.addAndGet(events.sumOf { it.count }.toInt())
            }
        }

        val currentTime = AtomicLong(0L)
        whenever(mockTimeProvider.getDeviceTimestampMillis()).thenAnswer { currentTime.get() }

        var periodicTask: Runnable? = null
        whenever(mockScheduledExecutor.scheduleAtFixedRate(any<Runnable>(), any(), any(), any())).thenAnswer {
            periodicTask = it.getArgument(0)
            mockScheduledFuture
        }
        whenever(mockScheduledExecutor.awaitTermination(any(), any())) doReturn true

        val aggregator = EvaluationAggregator(Int.MAX_VALUE)
        val processor = EvaluationEventsProcessor(
            writer = trackingWriter,
            timeProvider = mockTimeProvider,
            scheduledExecutor = mockScheduledExecutor,
            internalLogger = mockInternalLogger,
            flushIntervalMs = TEST_FLUSH_INTERVAL_MS,
            aggregator = aggregator
        )

        val threadCount = 5
        val iterationsPerThread = 50
        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(threadCount + 1)

        // Threads that add evaluations and manually flush
        val evalThreads = (1..threadCount).map { threadIndex ->
            Thread {
                startLatch.await()
                repeat(iterationsPerThread) { iteration ->
                    // Advance time slightly
                    currentTime.addAndGet(10)

                    processor.processEvaluation(
                        fakeFlagKey,
                        EvaluationContext(targetingKey = "user-$threadIndex-$iteration"),
                        fakeApplicationId,
                        fakeViewName,
                        fakeVariantKey,
                        fakeAllocationKey,
                        null,
                        null
                    )

                    // Occasionally do a manual flush
                    if (iteration % 10 == 0) {
                        processor.flush()
                    }
                }
                finishLatch.countDown()
            }
        }

        // Thread that runs the periodic task repeatedly
        val periodicThread = Thread {
            startLatch.await()
            repeat(iterationsPerThread * 2) {
                // Advance time past flush interval sometimes
                if (it % 5 == 0) {
                    currentTime.addAndGet(TEST_FLUSH_INTERVAL_MS)
                }
                checkNotNull(periodicTask).run()
                Thread.sleep(1)
            }
            finishLatch.countDown()
        }

        evalThreads.forEach { it.start() }
        periodicThread.start()
        startLatch.countDown()
        finishLatch.await()

        // Final flush to get any remaining
        currentTime.addAndGet(TEST_FLUSH_INTERVAL_MS)
        processor.stop()

        // All evaluations should be written exactly once
        assertThat(writeCount.get()).isEqualTo(threadCount * iterationsPerThread)
    }

    // endregion

    // region Helpers

    private fun createSchedulingEnabledProcessor(
        aggregator: EvaluationAggregator = testedAggregator
    ): EvaluationEventsProcessor = EvaluationEventsProcessor(
        writer = mockWriter,
        timeProvider = mockTimeProvider,
        scheduledExecutor = mockScheduledExecutor,
        internalLogger = mockInternalLogger,
        flushIntervalMs = TEST_FLUSH_INTERVAL_MS,
        aggregator = aggregator,
        periodicFlushEnabled = true
    )

    private fun processEval(
        flagKey: String = fakeFlagKey,
        context: EvaluationContext = fakeContext,
        variantKey: String? = fakeVariantKey,
        allocationKey: String? = fakeAllocationKey,
        errorCode: String? = null,
        errorMessage: String? = null
    ) {
        testedProcessor.processEvaluation(
            flagKey,
            context,
            fakeApplicationId,
            fakeViewName,
            variantKey,
            allocationKey,
            errorCode,
            errorMessage
        )
    }

    private fun captureWrittenEvents(): List<EvaluationAggregationStats> {
        val captor = argumentCaptor<List<EvaluationAggregationStats>>()
        verify(mockWriter).writeAll(captor.capture())
        return captor.allValues.flatten()
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
