/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.app.Application
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
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
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(ForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CompositionCaptureConcurrencyTest {

    private val fakeExecutors = mutableListOf<ExecutorService>()
    private val fakeWorkers = mutableListOf<Thread>()
    private val fakeCleanupActions = mutableListOf<() -> Unit>()

    @AfterEach
    fun tearDown() {
        fakeWorkers.filter(Thread::isAlive).forEach(Thread::interrupt)
        fakeCleanupActions.asReversed().forEach { it() }
        fakeExecutors.forEach(ExecutorService::shutdownNow)
        fakeWorkers.forEach { it.awaitCompletion() }
        fakeExecutors.forEach {
            assertThat(it.awaitTermination(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .describedAs("The test executor must terminate")
                .isTrue()
        }
    }

    @Test
    fun `M not throw W track() + tryAccept() { concurrent generation work }`(
        @IntForgery(min = 2, max = 5) fakeThreadCount: Int,
        forge: Forge
    ) {
        // Given
        val testedContext = forge.aGenerationContext(deadlineNs = Long.MAX_VALUE)
        val fakeFailures = CopyOnWriteArrayList<Throwable>()

        // When
        hammer(fakeThreadCount, fakeFailures) { threadIndex ->
            repeat(ITERATIONS) {
                val work = CancellableCaptureWork {}
                testedContext.track(work)
                testedContext.createWorkToken()?.complete()
                testedContext.release(work)
                if (threadIndex == 0 && it % INVALIDATION_INTERVAL == 0) testedContext.tryAccept()
            }
        }

        // Then
        assertThat(fakeFailures).isEmpty()
    }

    @Test
    fun `M expose consistent windows W update() + currentWindows() { concurrent replacement }`(
        @IntForgery(min = 2, max = 4) fakeReaderCount: Int,
        forge: Forge
    ) {
        // Given
        val testedWindowSource = ActiveWindowSource()
        val fakeFirstWindows = forge.aList(size = WINDOW_SET_SIZE) { mock<View>() }
        val fakeSecondWindows = forge.aList(size = WINDOW_SET_SIZE) { mock<View>() }
        val fakeFailures = CopyOnWriteArrayList<Throwable>()
        val fakeMixedReads = AtomicInteger(0)

        // When
        hammer(fakeReaderCount + 1, fakeFailures) { threadIndex ->
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
                    if (!isConsistent) fakeMixedReads.incrementAndGet()
                }
            }
        }

        // Then
        assertThat(fakeFailures).isEmpty()
        assertThat(fakeMixedReads).hasValue(0)
    }

    @Test
    fun `M not throw W intercept() + stop() { draw interception is refreshed and stopped from multiple threads }`(
        @IntForgery(min = 2, max = 4) fakeThreadCount: Int,
        forge: Forge
    ) {
        // Given
        val fakeDecorViews = forge.aList(size = WINDOW_SET_SIZE) { mockAliveDecorView() }
        val testedInterceptor = drawInterceptor()
        val fakeFailures = CopyOnWriteArrayList<Throwable>()

        // When
        hammer(fakeThreadCount, fakeFailures) { threadIndex ->
            repeat(ITERATIONS) {
                if (threadIndex == 0 && it % INVALIDATION_INTERVAL == 0) {
                    testedInterceptor.stop()
                } else {
                    testedInterceptor.intercept(fakeDecorViews.take(1 + it % WINDOW_SET_SIZE))
                }
            }
        }

        // Then
        assertThat(fakeFailures).isEmpty()
    }

    @Test
    fun `M not throw W intercept() + stop() { touch interception is refreshed and stopped from multiple threads }`(
        @IntForgery(min = 2, max = 4) fakeThreadCount: Int
    ) {
        // Given
        val fakeWindows = List(WINDOW_SET_SIZE) { mockTouchableWindow() }
        val testedInterceptor = touchInterceptor()
        val fakeFailures = CopyOnWriteArrayList<Throwable>()

        // When
        hammer(fakeThreadCount, fakeFailures) { threadIndex ->
            repeat(ITERATIONS) {
                if (threadIndex == 0 && it % INVALIDATION_INTERVAL == 0) {
                    testedInterceptor.stop()
                } else {
                    testedInterceptor.intercept(fakeWindows.take(1 + it % WINDOW_SET_SIZE))
                }
            }
        }

        // Then
        assertThat(fakeFailures).isEmpty()
    }

    @Test
    fun `M wrap a window at most once W intercept calls race on the same window`() {
        // Given
        val fakeRace = ForcedInterceptionRace()
        val mockWindow = mockTouchableWindow()
        whenever(mockWindow.callback).thenAnswer { fakeRace.pauseFirstAccess(); null }
        val testedInterceptor = touchInterceptor()

        // When
        fakeRace.run { testedInterceptor.intercept(listOf(mockWindow)) }

        // Then
        assertThat(fakeRace.failures).isEmpty()
        verify(mockWindow, times(1)).callback = isA()
    }

    @Test
    fun `M register a draw listener at most once per view W intercept calls race on the same view`() {
        // Given
        val fakeRace = ForcedInterceptionRace()
        val mockObserver = mock<ViewTreeObserver>()
        whenever(mockObserver.isAlive).thenReturn(true)
        val mockView = mock<View>()
        whenever(mockView.viewTreeObserver).thenAnswer { fakeRace.pauseFirstAccess(); mockObserver }
        val testedInterceptor = drawInterceptor()

        // When
        fakeRace.run { testedInterceptor.intercept(listOf(mockView)) }

        // Then
        assertThat(fakeRace.failures).isEmpty()
        verify(mockObserver, times(1)).addOnDrawListener(isA())
    }

    @Test
    fun `M keep one generation active W requestCapture() { draw signals arrive from multiple window threads }`(
        @IntForgery(min = 2, max = 5) fakeWindowThreadCount: Int,
        forge: Forge
    ) {
        // Given
        val fakeConcurrentCaptures = AtomicInteger(0)
        val fakeOverlappingCaptures = AtomicInteger(0)
        val fakeFixture = ConcurrencyFixture(
            forge,
            onCapture = {
                if (fakeConcurrentCaptures.incrementAndGet() > 1) fakeOverlappingCaptures.incrementAndGet()
                fakeConcurrentCaptures.decrementAndGet()
            }
        )
        val fakeFailures = CopyOnWriteArrayList<Throwable>()
        fakeFixture.testedOrchestrator.start()

        // When
        hammer(fakeWindowThreadCount, fakeFailures) {
            repeat(ITERATIONS) {
                fakeFixture.testedOrchestrator.requestCapture()
                fakeFixture.runScheduledCaptures()
            }
        }

        // Then
        assertThat(fakeFailures).isEmpty()
        assertThat(fakeOverlappingCaptures).hasValue(0)
        assertThat(fakeFixture.generationIds()).isNotEmpty().doesNotHaveDuplicates()
    }

    @Test
    fun `M resolve every generation exactly once W requestCapture() + stop() { expiry races a real async processor }`(
        @IntForgery(min = 2, max = 4) fakeWindowThreadCount: Int,
        forge: Forge
    ) {
        // Given
        val fakeFixture = AsyncExpiryRaceFixture(forge, RACE_GENERATION_BUDGET_NS, PROCESSOR_DELAY_RANGE_MS)
        val fakeFailures = CopyOnWriteArrayList<Throwable>()
        fakeFixture.testedOrchestrator.start()

        // When
        val deadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RACE_DURATION_MS)
        hammer(fakeWindowThreadCount, fakeFailures) {
            while (System.nanoTime() < deadlineNs) fakeFixture.testedOrchestrator.requestCapture()
        }
        Thread.sleep(SETTLE_MS)
        fakeFixture.testedOrchestrator.stop()
        val processorsTerminated = fakeFixture.stopExecutors()

        // Then
        assertThat(processorsTerminated).isTrue()
        assertThat(fakeFailures).isEmpty()
        assertThat(fakeFixture.createdGenerations).isNotEmpty()
        assertThat(fakeFixture.consumedGenerationIds)
            .describedAs("a generation resolved by both expiry and the processor would be consumed twice")
            .doesNotHaveDuplicates()
        assertThat(fakeFixture.createdGenerations.filter { it.isActive() })
            .describedAs("no generation may stay active once the pipeline is stopped and in-flight work settles")
            .isEmpty()
    }

    @Test
    fun `M expire every generation W stop() { captures in flight }`(
        @IntForgery(min = 2, max = 4) fakeWindowThreadCount: Int,
        forge: Forge
    ) {
        // Given
        val fakeFixture = ConcurrencyFixture(forge)
        val fakeFailures = CopyOnWriteArrayList<Throwable>()
        fakeFixture.testedOrchestrator.start()

        // When
        hammer(fakeWindowThreadCount + 1, fakeFailures) { threadIndex ->
            if (threadIndex == 0) {
                repeat(ITERATIONS / INVALIDATION_INTERVAL) {
                    fakeFixture.testedOrchestrator.stop()
                    fakeFixture.testedOrchestrator.start()
                }
            } else {
                repeat(ITERATIONS) {
                    fakeFixture.testedOrchestrator.requestCapture()
                    fakeFixture.runScheduledCaptures()
                }
            }
        }
        fakeFixture.testedOrchestrator.stop()
        fakeFixture.testedCompletionQueue.stop()

        // Then
        assertThat(fakeFailures).isEmpty()
        assertThat(fakeFixture.createdGenerations.filter { it.isActive() })
            .describedAs("no generation may stay active once the pipeline is stopped")
            .isEmpty()
    }

    @Test
    fun `M not process queued captures W consume() + stop() { the completion queue stops during processing }`(
        @IntForgery(min = 2, max = 4) fakeQueuedCaptureCount: Int,
        forge: Forge
    ) {
        // Given
        val fakeProcessorEntered = CountDownLatch(1)
        val fakeStopCompleted = CountDownLatch(1)
        val fakeProcessedCaptures = CopyOnWriteArrayList<CompletedSnapshotCapture>()
        val fakeExecutorService = Executors.newSingleThreadExecutor().also(fakeExecutors::add)
        val testedQueue = SnapshotCompletionQueue(
            executorService = fakeExecutorService,
            processor = SnapshotCompletionProcessor { capture ->
                fakeProcessedCaptures += capture
                fakeProcessorEntered.countDown()
                fakeStopCompleted.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            },
            internalLogger = mock<InternalLogger>(),
            maxQueuedCaptures = fakeQueuedCaptureCount + 1
        ).also { fakeCleanupActions += it::stop }
        val fakeCaptures = forge.aList(size = fakeQueuedCaptureCount + 1) { aCompletedCapture() }

        // When
        fakeCaptures.forEach(testedQueue::consume)
        val processorStarted = fakeProcessorEntered.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        testedQueue.stop()
        fakeStopCompleted.countDown()
        fakeExecutorService.shutdownNow()
        val executorTerminated = fakeExecutorService.awaitTermination(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)

        // Then
        assertThat(processorStarted).isTrue()
        assertThat(executorTerminated).isTrue()
        assertThat(fakeProcessedCaptures).hasSize(1)
        assertThat(fakeCaptures.filter { it.generation.isActive() })
            .describedAs("stop() must expire the in-flight capture and everything still queued")
            .isEmpty()
    }

    @Test
    fun `M expire oldest queued capture W consume() { concurrent completion blocked }`(
        @IntForgery(min = 1, max = 4) fakeMaxQueuedCaptures: Int,
        forge: Forge
    ) {
        // Given
        val fakeFixture = QueueSaturationFixture(forge, fakeMaxQueuedCaptures)

        // When
        fakeFixture.fakeOverflowingCaptures.forEach(fakeFixture.testedQueue::consume)

        // Then
        assertThat(fakeFixture.fakeOverflowingCaptures.filterNot { it.generation.isActive() })
            .containsExactly(fakeFixture.fakeOverflowingCaptures.first())
    }

    @Test
    fun `M skip dropped capture W releaseAndDrain() { concurrent completion queue saturated }`(
        @IntForgery(min = 1, max = 4) fakeMaxQueuedCaptures: Int,
        forge: Forge
    ) {
        // Given
        val fakeFixture = QueueSaturationFixture(forge, fakeMaxQueuedCaptures)
        fakeFixture.fakeOverflowingCaptures.forEach(fakeFixture.testedQueue::consume)

        // When
        fakeFixture.releaseAndDrain()

        // Then
        assertThat(fakeFixture.fakeProcessedCaptures).doesNotContain(fakeFixture.fakeOverflowingCaptures.first())
    }

    /** Parks the first interception at its getter until the second interception returns. */
    private inner class ForcedInterceptionRace {
        private val firstEntered = CountDownLatch(1)
        private val releaseFirst = CountDownLatch(1)
        private val firstAccess = AtomicBoolean(true)
        val failures = CopyOnWriteArrayList<Throwable>()

        fun pauseFirstAccess() {
            if (firstAccess.compareAndSet(true, false)) {
                firstEntered.countDown()
                check(releaseFirst.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    "The first interception was not released"
                }
            }
        }

        fun run(intercept: () -> Unit) {
            val first = worker(failures, intercept)
            first.start()
            try {
                check(firstEntered.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    "The first interception did not reach the blocked getter"
                }
                val second = worker(failures, intercept)
                second.start()
                second.awaitCompletion()
            } finally {
                releaseFirst.countDown()
            }
            first.awaitCompletion()
        }
    }

    private inner class AsyncExpiryRaceFixture(
        forge: Forge,
        generationBudgetNs: Long,
        processorDelayRangeMs: LongRange
    ) {
        private val expiryExecutor = Executors.newScheduledThreadPool(2).also(fakeExecutors::add)
        private val processorExecutor = Executors.newFixedThreadPool(2).also(fakeExecutors::add)
        val createdGenerations = CopyOnWriteArrayList<CaptureGenerationContext>()
        val consumedGenerationIds = ConcurrentLinkedQueue<Long>()
        private val fakeProcessorDelaysMs = forge.aList(size = ITERATIONS) {
            aLong(min = processorDelayRangeMs.first, max = processorDelayRangeMs.last)
        }
        private val fakeSnapshot = forge.aCompositionTestTree().snapshot
        val testedOrchestrator = SnapshotCaptureOrchestrator(
            producer = CapturedSnapshotProducer { generation ->
                createdGenerations += generation
                fakeSnapshot
            },
            processor = CapturedSnapshotProcessor { request, callback ->
                val fakeDelayIndex = (request.generation.id % fakeProcessorDelaysMs.size).toInt()
                val fakeDelayMs = fakeProcessorDelaysMs[fakeDelayIndex]
                val future = processorExecutor.submit {
                    Thread.sleep(fakeDelayMs)
                    callback.onProcessed(
                        SnapshotProcessingResult.Completed(
                            request.generation.id,
                            request.snapshot as CapturedFullSnapshot
                        )
                    )
                }
                CancellableCaptureWork { future.cancel(false) }
            },
            consumer = CompletedSnapshotConsumer { capture -> consumedGenerationIds += capture.generation.id },
            timeProvider = { System.nanoTime() },
            captureScheduler = { _, task -> task(); CancellableCaptureWork.NONE },
            mainThreadExecutor = { task -> task(); CancellableCaptureWork.NONE },
            expiryScheduler = ExecutorCaptureTaskScheduler(expiryExecutor, mock()),
            generationBudgetNs = generationBudgetNs,
            internalLogger = mock()
        ).also { fakeCleanupActions += it::stop }

        fun stopExecutors(): Boolean {
            processorExecutor.shutdown()
            expiryExecutor.shutdownNow()
            return processorExecutor.awaitTermination(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }
    }

    private inner class QueueSaturationFixture(forge: Forge, fakeCapacity: Int) {
        private val fakeDrainBlocked = CountDownLatch(1)
        private val fakeProcessorEntered = CountDownLatch(1)
        val fakeProcessedCaptures = CopyOnWriteArrayList<CompletedSnapshotCapture>()
        private val fakeExecutor = Executors.newSingleThreadExecutor().also(fakeExecutors::add)
        val testedQueue = SnapshotCompletionQueue(
            executorService = fakeExecutor,
            processor = SnapshotCompletionProcessor { capture ->
                fakeProcessedCaptures += capture
                fakeProcessorEntered.countDown()
                fakeDrainBlocked.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            },
            internalLogger = mock<InternalLogger>(),
            maxQueuedCaptures = fakeCapacity
        ).also { fakeCleanupActions += it::stop }
        val fakeOverflowingCaptures = forge.aList(size = fakeCapacity + 1) { aCompletedCapture() }

        init {
            testedQueue.consume(forge.aCompletedCapture())
            check(fakeProcessorEntered.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                "Completion worker must enter processing before the queue is saturated"
            }
        }

        fun releaseAndDrain() {
            fakeDrainBlocked.countDown()
            fakeExecutor.shutdown()
            check(fakeExecutor.awaitTermination(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                "Completion worker must drain the retained captures"
            }
        }
    }

    private fun hammer(
        threadCount: Int,
        failures: MutableList<Throwable>,
        block: (threadIndex: Int) -> Unit
    ) {
        val startLine = CountDownLatch(1)
        val threads = List(threadCount) { threadIndex ->
            worker(failures) {
                check(startLine.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    "Workers did not reach the start line"
                }
                assertDoesNotThrow { block(threadIndex) }
            }
        }
        threads.forEach(Thread::start)
        startLine.countDown()
        threads.forEach { it.awaitCompletion() }
    }

    private fun worker(failures: MutableList<Throwable>, block: () -> Unit): Thread = Thread {
        try {
            block()
        } catch (error: Throwable) {
            failures += error
        }
    }.also { fakeThread ->
        fakeThread.isDaemon = true
        fakeWorkers += fakeThread
    }

    private fun drawInterceptor() = CompositionViewOnDrawInterceptor(
        windowSource = ActiveWindowSource(),
        onWindowsChanged = CompositionChangeListener { },
        internalLogger = mock()
    )

    private fun touchInterceptor() = CompositionWindowTouchInterceptor(
        appContext = mockTouchableAppContext(),
        recordWriter = mock(),
        timeProvider = mock(),
        rumContextProvider = mock(),
        touchPrivacyManager = mock(),
        internalLogger = mock(),
        windowSource = ActiveWindowSource()
    )

    private fun Thread.awaitCompletion() {
        join(AWAIT_TIMEOUT_MS)
        assertThat(isAlive).describedAs("Worker %s must finish", name).isFalse()
    }

    private fun mockAliveDecorView(): View {
        val mockObserver = mock<ViewTreeObserver>()
        whenever(mockObserver.isAlive).thenReturn(true)
        val mockView = mock<View>()
        whenever(mockView.viewTreeObserver).thenReturn(mockObserver)
        return mockView
    }

    private fun mockTouchableWindow(): Window = mock {
        whenever(it.peekDecorView()).thenReturn(mock<View>())
    }

    private fun mockTouchableAppContext(): Application = mock { mockApplication ->
        val fakeDisplayMetrics = DisplayMetrics()
        val mockResources = mock<Resources> { whenever(it.displayMetrics).thenReturn(fakeDisplayMetrics) }
        whenever(mockApplication.resources).thenReturn(mockResources)
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
    private inner class ConcurrencyFixture(
        forge: Forge,
        onCapture: () -> Unit = {}
    ) {
        val createdGenerations = CopyOnWriteArrayList<CaptureGenerationContext>()
        val testedCompletionQueue = SnapshotCompletionQueue(
            executorService = Executors.newSingleThreadExecutor().also(fakeExecutors::add),
            // Mirrors the production processor contract: every capture ends accepted or expired.
            processor = SnapshotCompletionProcessor { it.generation.tryAccept() },
            internalLogger = mock<InternalLogger>()
        ).also { fakeCleanupActions += it::stop }
        private val scheduledCaptures = ConcurrentLinkedQueue<() -> Unit>()
        private val snapshot = forge.aCompositionTestTree().snapshot
        val testedOrchestrator = SnapshotCaptureOrchestrator(
            producer = CapturedSnapshotProducer { generation ->
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
        ).also { fakeCleanupActions += it::stop }

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
