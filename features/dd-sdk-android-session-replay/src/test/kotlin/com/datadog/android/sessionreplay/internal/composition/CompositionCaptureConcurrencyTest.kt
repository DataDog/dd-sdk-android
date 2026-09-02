/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View
import android.view.ViewTreeObserver
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Every collaborator here is built once per recording session and reached from per-window draw
 * callbacks, so each one is exercised from several threads at once.
 *
 * Same-structure races are hammered; ordering races are reproduced with a forced interleaving,
 * because hammering passes on broken code when the two operations only have to stay in a consistent
 * relative order.
 */
@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CompositionCaptureConcurrencyTest {

    @Test
    fun `M not throw W generation work is tracked and invalidated from multiple threads`(
        @IntForgery(min = 2, max = 5) fakeThreadCount: Int,
        forge: Forge
    ) {
        // Given
        val testedContext = forge.aGenerationContext(deadlineNs = Long.MAX_VALUE)
        val failures = CopyOnWriteArrayList<Throwable>()

        // When
        hammer(fakeThreadCount, failures) { threadIndex ->
            repeat(ITERATIONS) {
                val work = CancellableCaptureWork {}
                testedContext.track(work)
                testedContext.createWorkToken()?.complete()
                testedContext.release(work)
                if (threadIndex == 0 && it % INVALIDATION_INTERVAL == 0) testedContext.tryAccept()
            }
        }

        // Then
        assertThat(failures).isEmpty()
    }

    @Test
    fun `M never expose a partially updated window set W windows are read while being replaced`(
        @IntForgery(min = 2, max = 4) fakeReaderCount: Int,
        forge: Forge
    ) {
        // Given
        val testedWindowSource = ActiveWindowSource()
        val fakeFirstWindows = forge.aList(size = WINDOW_SET_SIZE) { mock<View>() }
        val fakeSecondWindows = forge.aList(size = WINDOW_SET_SIZE) { mock<View>() }
        val failures = CopyOnWriteArrayList<Throwable>()
        val mixedReads = AtomicInteger(0)

        // When
        hammer(fakeReaderCount + 1, failures) { threadIndex ->
            if (threadIndex == 0) {
                repeat(ITERATIONS) {
                    testedWindowSource.update(if (it % 2 == 0) fakeFirstWindows else fakeSecondWindows)
                }
            } else {
                repeat(ITERATIONS) {
                    val windows = testedWindowSource.currentWindows()
                    val isConsistent = windows.isEmpty() ||
                        windows == fakeFirstWindows ||
                        windows == fakeSecondWindows
                    if (!isConsistent) mixedReads.incrementAndGet()
                }
            }
        }

        // Then
        assertThat(failures).isEmpty()
        assertThat(mixedReads).hasValue(0)
    }

    @Test
    fun `M not throw W draw interception is refreshed and stopped from multiple threads`(
        @IntForgery(min = 2, max = 4) fakeThreadCount: Int,
        forge: Forge
    ) {
        // Given
        val fakeDecorViews = forge.aList(size = WINDOW_SET_SIZE) { aliveDecorView() }
        val testedInterceptor = CompositionViewOnDrawInterceptor(
            windowSource = ActiveWindowSource(),
            onWindowsChanged = CompositionChangeListener { },
            internalLogger = mock()
        )
        val failures = CopyOnWriteArrayList<Throwable>()

        // When
        hammer(fakeThreadCount, failures) { threadIndex ->
            repeat(ITERATIONS) {
                if (threadIndex == 0 && it % INVALIDATION_INTERVAL == 0) {
                    testedInterceptor.stop()
                } else {
                    testedInterceptor.intercept(fakeDecorViews.take(1 + it % WINDOW_SET_SIZE))
                }
            }
        }

        // Then
        assertThat(failures).isEmpty()
    }

    @Test
    fun `M register a draw listener at most once per view W intercept calls race on the same view`() {
        // Given
        // Forced interleaving: thread A is parked exactly where addListener reads the view's
        // ViewTreeObserver - after intercept()'s synchronized "is this view new" check already said
        // yes, but before anything is registered or recorded. Thread B then runs intercept() for the
        // same view while thread A is parked there, so both threads independently decide the view is
        // new and both proceed to register a listener for it.
        val enteredViewTreeObserver = CountDownLatch(1)
        val releaseViewTreeObserver = CountDownLatch(1)
        val blockNextAccess = AtomicBoolean(true)
        val mockObserver = mock<ViewTreeObserver>()
        whenever(mockObserver.isAlive).thenReturn(true)
        val fakeView = mock<View>()
        whenever(fakeView.viewTreeObserver).thenAnswer {
            if (blockNextAccess.compareAndSet(true, false)) {
                enteredViewTreeObserver.countDown()
                releaseViewTreeObserver.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }
            mockObserver
        }
        val testedInterceptor = CompositionViewOnDrawInterceptor(
            windowSource = ActiveWindowSource(),
            onWindowsChanged = CompositionChangeListener { },
            internalLogger = mock()
        )
        val failures = CopyOnWriteArrayList<Throwable>()

        // When
        val threadA = Thread {
            try {
                testedInterceptor.intercept(listOf(fakeView))
            } catch (e: Throwable) {
                failures += e
            }
        }
        threadA.start()
        assertThat(enteredViewTreeObserver.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue()

        val threadB = Thread {
            try {
                testedInterceptor.intercept(listOf(fakeView))
            } catch (e: Throwable) {
                failures += e
            }
        }
        threadB.start()
        threadB.join(AWAIT_TIMEOUT_MS)
        releaseViewTreeObserver.countDown()
        threadA.join(AWAIT_TIMEOUT_MS)

        // Then
        assertThat(failures).isEmpty()
        verify(mockObserver, times(1)).addOnDrawListener(any())
    }

    @Test
    fun `M keep one generation active W draw signals arrive from multiple window threads`(
        @IntForgery(min = 2, max = 5) fakeWindowThreadCount: Int,
        forge: Forge
    ) {
        // Given
        val concurrentCaptures = AtomicInteger(0)
        val overlappingCaptures = AtomicInteger(0)
        val fixture = ConcurrencyFixture(
            forge,
            onCapture = {
                if (concurrentCaptures.incrementAndGet() > 1) overlappingCaptures.incrementAndGet()
                concurrentCaptures.decrementAndGet()
            }
        )
        val failures = CopyOnWriteArrayList<Throwable>()
        fixture.testedOrchestrator.start()

        // When
        hammer(fakeWindowThreadCount, failures) {
            repeat(ITERATIONS) {
                fixture.testedOrchestrator.requestCapture(CompositionChangeset.of(listOf(mock<View>())))
                fixture.runScheduledCaptures()
            }
        }

        // Then
        assertThat(failures).isEmpty()
        assertThat(overlappingCaptures).hasValue(0)
        assertThat(fixture.generationIds()).doesNotHaveDuplicates()
    }

    @Test
    fun `M resolve every generation exactly once W expiry races a real async processor`(
        @IntForgery(min = 2, max = 4) fakeWindowThreadCount: Int,
        forge: Forge
    ) {
        // Given
        // Both schedulers below are real thread pools, distinct from the window threads and from
        // each other: the expiry timer and the processor's completion callback genuinely race on
        // separate threads for the same generation, unlike the other tests in this file which keep
        // one of the two synchronous to test ordering instead.
        val expiryExecutor = Executors.newScheduledThreadPool(2)
        val processorExecutor = Executors.newFixedThreadPool(2)
        val createdGenerations = CopyOnWriteArrayList<CaptureGenerationContext>()
        val consumedGenerationIds = ConcurrentLinkedQueue<Long>()
        val failures = CopyOnWriteArrayList<Throwable>()
        val snapshot = forge.aCompositionTestTree().snapshot
        val testedOrchestrator = SnapshotCaptureOrchestrator(
            producer = CapturedSnapshotProducer { generation, _ ->
                createdGenerations += generation
                snapshot
            },
            processor = CapturedSnapshotProcessor { request, callback ->
                val future = processorExecutor.submit {
                    Thread.sleep(PROCESSOR_DELAY_RANGE_MS.random())
                    callback.onProcessed(
                        SnapshotProcessingResult.Completed(request.generation.id, request.snapshot)
                    )
                }
                CancellableCaptureWork { future.cancel(false) }
            },
            consumer = CompletedSnapshotConsumer { capture -> consumedGenerationIds += capture.generation.id },
            timeProvider = { System.nanoTime() },
            captureScheduler = { _, task ->
                task()
                CancellableCaptureWork.NONE
            },
            mainThreadExecutor = { task ->
                task()
                CancellableCaptureWork.NONE
            },
            expiryScheduler = ScheduledExecutorCaptureTaskScheduler(
                executorService = expiryExecutor,
                internalLogger = mock()
            ),
            generationBudgetNs = RACE_GENERATION_BUDGET_NS,
            internalLogger = mock()
        )
        testedOrchestrator.start()

        // When
        val deadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RACE_DURATION_MS)
        hammer(fakeWindowThreadCount, failures) {
            while (System.nanoTime() < deadlineNs) {
                testedOrchestrator.requestCapture(CompositionChangeset.of(listOf(mock<View>())))
            }
        }
        // Lets whichever generation is still in flight resolve, via either the expiry timer or the
        // processor callback, before the pipeline is torn down.
        Thread.sleep(SETTLE_MS)
        testedOrchestrator.stop()
        processorExecutor.shutdown()
        expiryExecutor.shutdownNow()
        assertThat(processorExecutor.awaitTermination(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue()

        // Then
        assertThat(failures).isEmpty()
        assertThat(createdGenerations).isNotEmpty()
        assertThat(consumedGenerationIds)
            .describedAs("a generation resolved by both expiry and the processor would be consumed twice")
            .doesNotHaveDuplicates()
        assertThat(createdGenerations.filter { it.isActive() })
            .describedAs("no generation may stay active once the pipeline is stopped and in-flight work settles")
            .isEmpty()
    }

    @Test
    fun `M expire every generation W the pipeline is stopped while captures are in flight`(
        @IntForgery(min = 2, max = 4) fakeWindowThreadCount: Int,
        forge: Forge
    ) {
        // Given
        val fixture = ConcurrencyFixture(forge)
        val failures = CopyOnWriteArrayList<Throwable>()
        fixture.testedOrchestrator.start()

        // When
        hammer(fakeWindowThreadCount + 1, failures) { threadIndex ->
            if (threadIndex == 0) {
                repeat(ITERATIONS / INVALIDATION_INTERVAL) {
                    fixture.testedOrchestrator.stop()
                    fixture.testedOrchestrator.start()
                }
            } else {
                repeat(ITERATIONS) {
                    fixture.testedOrchestrator.requestCapture()
                    fixture.runScheduledCaptures()
                }
            }
        }
        fixture.testedOrchestrator.stop()
        fixture.testedCompletionQueue.stop()

        // Then
        assertThat(failures).isEmpty()
        assertThat(fixture.createdGenerations.filter { it.isActive() })
            .describedAs("no generation may stay active once the pipeline is stopped")
            .isEmpty()
    }

    @Test
    fun `M not process queued captures W the completion queue stops during processing`(
        @IntForgery(min = 2, max = 4) fakeQueuedCaptureCount: Int,
        forge: Forge
    ) {
        // Given
        // Forced interleaving: the processor parks inside its critical section so that stop() is
        // guaranteed to land while one capture is being processed and the rest are still queued.
        val processorEntered = CountDownLatch(1)
        val stopCompleted = CountDownLatch(1)
        val processedCaptures = CopyOnWriteArrayList<CompletedSnapshotCapture>()
        val executorService = Executors.newSingleThreadExecutor()
        val testedQueue = SnapshotCompletionQueue(
            executorService = executorService,
            processor = SnapshotCompletionProcessor { capture ->
                processedCaptures += capture
                processorEntered.countDown()
                stopCompleted.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            },
            internalLogger = mock<InternalLogger>(),
            maxQueuedCaptures = fakeQueuedCaptureCount + 1
        )
        val fakeCaptures = forge.aList(size = fakeQueuedCaptureCount + 1) { aCompletedCapture() }

        // When
        fakeCaptures.forEach(testedQueue::consume)
        assertThat(processorEntered.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue()
        testedQueue.stop()
        stopCompleted.countDown()
        executorService.shutdownNow()
        executorService.awaitTermination(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)

        // Then
        assertThat(processedCaptures).hasSize(1)
        assertThat(fakeCaptures.filter { it.generation.isActive() })
            .describedAs("stop() must expire the in-flight capture and everything still queued")
            .isEmpty()
    }

    @Test
    fun `M drop the oldest capture W the completion queue saturates`(
        @IntForgery(min = 1, max = 4) fakeMaxQueuedCaptures: Int,
        forge: Forge
    ) {
        // Given
        val drainBlocked = CountDownLatch(1)
        val processorEntered = CountDownLatch(1)
        val processedCaptures = CopyOnWriteArrayList<CompletedSnapshotCapture>()
        val executorService = Executors.newSingleThreadExecutor()
        val testedQueue = SnapshotCompletionQueue(
            executorService = executorService,
            processor = SnapshotCompletionProcessor { capture ->
                processedCaptures += capture
                processorEntered.countDown()
                drainBlocked.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            },
            internalLogger = mock<InternalLogger>(),
            maxQueuedCaptures = fakeMaxQueuedCaptures
        )
        val fakeBlockingCapture = forge.aCompletedCapture()

        // When
        testedQueue.consume(fakeBlockingCapture)
        assertThat(processorEntered.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue()
        val fakeOverflowingCaptures = forge.aList(size = fakeMaxQueuedCaptures + 1) { aCompletedCapture() }
        fakeOverflowingCaptures.forEach(testedQueue::consume)

        // Then
        val expiredCaptures = fakeOverflowingCaptures.filterNot { it.generation.isActive() }
        assertThat(expiredCaptures)
            .describedAs("the oldest queued capture is dropped and expired once the queue saturates")
            .containsExactly(fakeOverflowingCaptures.first())

        // When
        drainBlocked.countDown()
        executorService.shutdown()
        executorService.awaitTermination(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)

        // Then
        assertThat(processedCaptures).doesNotContain(fakeOverflowingCaptures.first())
    }

    private fun hammer(
        threadCount: Int,
        failures: MutableList<Throwable>,
        block: (threadIndex: Int) -> Unit
    ) {
        val startLine = CountDownLatch(1)
        val threads = List(threadCount) { threadIndex ->
            Thread {
                try {
                    startLine.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    block(threadIndex)
                } catch (e: Throwable) {
                    failures += e
                }
            }
        }
        threads.forEach(Thread::start)
        startLine.countDown()
        threads.forEach { it.join(AWAIT_TIMEOUT_MS) }
    }

    private fun aliveDecorView(): View {
        val mockObserver = mock<ViewTreeObserver>()
        whenever(mockObserver.isAlive).thenReturn(true)
        val mockView = mock<View>()
        whenever(mockView.viewTreeObserver).thenReturn(mockObserver)
        return mockView
    }

    private fun Forge.aCompletedCapture(): CompletedSnapshotCapture = CompletedSnapshotCapture(
        generation = aGenerationContext(deadlineNs = Long.MAX_VALUE),
        snapshot = aCompositionTestTree().snapshot
    )

    private fun Forge.aGenerationContext(
        deadlineNs: Long = Long.MAX_VALUE
    ): CaptureGenerationContext = CaptureGenerationContext(
        id = aLong(min = 1L, max = 1_000L),
        startedAtNs = 0L,
        deadlineNs = deadlineNs,
        timeProvider = { 0L }
    )

    /** Drives the orchestrator entirely on the calling thread, the way a draw callback does. */
    private class ConcurrencyFixture(
        forge: Forge,
        onCapture: () -> Unit = {}
    ) {
        val createdGenerations = CopyOnWriteArrayList<CaptureGenerationContext>()
        val testedCompletionQueue = SnapshotCompletionQueue(
            executorService = Executors.newSingleThreadExecutor(),
            // Mirrors the production processor contract: every capture ends accepted or expired.
            processor = SnapshotCompletionProcessor { it.generation.tryAccept() },
            internalLogger = mock<InternalLogger>()
        )
        private val scheduledCaptures = ConcurrentLinkedQueue<() -> Unit>()
        private val snapshot = forge.aCompositionTestTree().snapshot
        val testedOrchestrator = SnapshotCaptureOrchestrator(
            producer = CapturedSnapshotProducer { generation, _ ->
                createdGenerations += generation
                onCapture()
                snapshot
            },
            processor = ImmediateCapturedSnapshotProcessor(),
            consumer = testedCompletionQueue,
            timeProvider = { System.nanoTime() },
            captureScheduler = { _, task ->
                scheduledCaptures += task
                CancellableCaptureWork { scheduledCaptures.remove(task) }
            },
            mainThreadExecutor = { task ->
                task()
                CancellableCaptureWork.NONE
            },
            expiryScheduler = { _, _ -> CancellableCaptureWork.NONE },
            internalLogger = mock<InternalLogger>()
        )

        fun runScheduledCaptures() {
            while (true) {
                val task = scheduledCaptures.poll() ?: break
                task()
            }
        }

        fun generationIds(): List<Long> = createdGenerations.map(CaptureGenerationContext::id)
    }

    private companion object {
        const val ITERATIONS = 2_000
        const val INVALIDATION_INTERVAL = 50
        const val WINDOW_SET_SIZE = 3
        const val AWAIT_TIMEOUT_MS = 10_000L
        const val RACE_DURATION_MS = 300L
        const val SETTLE_MS = 200L
        val RACE_GENERATION_BUDGET_NS = TimeUnit.MICROSECONDS.toNanos(500)
        val PROCESSOR_DELAY_RANGE_MS = 0L..2L
    }
}
