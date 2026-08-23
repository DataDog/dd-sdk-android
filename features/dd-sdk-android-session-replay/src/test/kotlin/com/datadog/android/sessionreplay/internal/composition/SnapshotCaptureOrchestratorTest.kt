/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View
import com.datadog.android.sessionreplay.internal.recorder.RecordingTimeBank
import com.datadog.android.sessionreplay.internal.recorder.TimeBank
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

internal class SnapshotCaptureOrchestratorTest {

    @Test
    fun `M use 90 ms deadline W generation budget is not overridden`() {
        // Given
        val clock = FakeClock().apply { nowNs = 42L }
        val scheduler = FakeScheduler()
        val expiryScheduler = FakeScheduler()
        val generations = mutableListOf<CaptureGenerationContext>()
        val orchestrator = SnapshotCaptureOrchestrator(
            producer = CapturedSnapshotProducer { context, _ ->
                generations += context
                null
            },
            processor = FakeProcessor(),
            consumer = CompletedSnapshotConsumer { _ -> },
            timeProvider = clock,
            captureScheduler = scheduler,
            mainThreadExecutor = FakeMainThreadExecutor(),
            expiryScheduler = expiryScheduler,
            captureDelayNs = IMMEDIATE
        )

        // When
        orchestrator.start()
        orchestrator.requestCapture()
        scheduler.runNext(IMMEDIATE)

        // Then
        assertThat(generations.single().deadlineNs - generations.single().startedAtNs)
            .isEqualTo(90_000_000L)
    }

    @Test
    fun `M keep recording allowance independent W generation deadline is 90 ms`() {
        // Given
        val recordingTimeBank = RecordingTimeBank()
        val generation = CaptureGenerationContext(1L, 0L, 90_000_000L, FakeClock())

        // When
        val initiallyAdmitted = recordingTimeBank.updateAndCheck(0L)
        recordingTimeBank.consume(100_000_001L)

        // Then
        assertThat(initiallyAdmitted).isTrue()
        assertThat(recordingTimeBank.updateAndCheck(0L)).isFalse()
        assertThat(generation.remainingBudgetNs()).isEqualTo(90_000_000L)
    }

    @Test
    fun `M discover roots on main thread W generation starts`() {
        // Given
        val mainThread = FakeMainThreadExecutor(autoRun = false)
        val fixture = Fixture(mainThreadExecutor = mainThread)
        fixture.orchestrator.start()

        // When
        fixture.orchestrator.requestCapture()
        fixture.scheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fixture.producerCaptures).isZero()

        // When
        mainThread.runNext()

        // Then
        assertThat(fixture.producerCaptures).isEqualTo(1)
    }

    @Test
    fun `M execute synchronous capture adapter on main thread W capture starts`() {
        // Given
        val mainThread = FakeMainThreadExecutor()
        var producerRanOnMainThread = false
        val fixture = Fixture(
            mainThreadExecutor = mainThread,
            onProducerCapture = { producerRanOnMainThread = mainThread.isExecuting }
        )

        // When
        fixture.orchestrator.start()
        fixture.orchestrator.requestCapture()
        fixture.scheduler.runNext(IMMEDIATE)

        // Then
        assertThat(producerRanOnMainThread).isTrue()
    }

    @Test
    fun `M release main thread W generation waits for asynchronous processing`() {
        // Given
        val mainThread = FakeMainThreadExecutor()
        val fixture = Fixture(mainThreadExecutor = mainThread)
        fixture.orchestrator.start()

        // When
        fixture.orchestrator.requestCapture()
        fixture.scheduler.runNext(IMMEDIATE)

        // Then: processing itself is dispatched off the main thread, not called inline
        assertThat(fixture.processor.pending).isEmpty()
        assertThat(mainThread.isExecuting).isFalse()
        assertThat(fixture.consumed).isEmpty()

        // When
        fixture.expiryScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fixture.processor.pending).hasSize(1)
        assertThat(fixture.consumed).isEmpty()
    }

    @Test
    fun `M cancel dispatched processing W generation expires before dispatch runs`() {
        // Given
        val fixture = Fixture()
        fixture.orchestrator.start()
        fixture.orchestrator.requestCapture()
        fixture.scheduler.runNext(IMMEDIATE)

        // When
        fixture.expiryScheduler.runNext(TIMEOUT_NS)

        // Then
        assertThat(fixture.expiryScheduler.tasks.single { it.delayNs == IMMEDIATE }.cancelled).isTrue()
        assertThat(fixture.processor.pending).isEmpty()
    }

    @Test
    fun `M hand off completed snapshot W processing completes in generation`() {
        // Given
        val fixture = Fixture()

        // When
        fixture.orchestrator.start()
        fixture.orchestrator.requestCapture()
        fixture.scheduler.runNext(IMMEDIATE)
        fixture.expiryScheduler.runNext(IMMEDIATE)
        val processing = fixture.processor.pending.single()
        processing.complete()

        // Then
        assertThat(fixture.producerCaptures).isEqualTo(1)
        assertThat(fixture.consumed).hasSize(1)
        assertThat(fixture.consumed.single().generation).isEqualTo(processing.request.generation)
        assertThat(fixture.consumed.single().snapshot).isSameAs(fixture.snapshot)
        // The expiry timer is deliberately never cancelled - see the fire-and-forget comment in
        // SnapshotCaptureOrchestrator.beginCapture() - so it is still sitting there, unresolved,
        // even after the generation it was guarding against has already completed successfully.
        assertThat(fixture.expiryScheduler.tasks.single { it.delayNs == TIMEOUT_NS }.cancelled).isFalse()
    }

    @Test
    fun `M coalesce a follow-up capture W draw signals arrive during processing`() {
        // Given
        val fixture = Fixture()
        fixture.orchestrator.start()
        fixture.orchestrator.requestCapture()
        fixture.scheduler.runNext(IMMEDIATE)
        fixture.expiryScheduler.runNext(IMMEDIATE)

        // When
        fixture.orchestrator.requestCapture()
        fixture.orchestrator.requestCapture()
        fixture.processor.pending.single().complete()
        fixture.scheduler.runNext(IMMEDIATE)
        fixture.expiryScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fixture.producerCaptures).isEqualTo(2)
        assertThat(fixture.processor.pending).hasSize(2)
        assertThat(fixture.processor.pending.map { it.request.generation.id }).containsExactly(1L, 2L)
    }

    @Test
    fun `M pass changeset to producer W requestCapture supplies one`() {
        // Given
        val fixture = Fixture()
        val view = mock<View>()
        fixture.orchestrator.start()

        // When
        fixture.orchestrator.requestCapture(CompositionChangeset.of(listOf(view)))
        fixture.scheduler.runNext(IMMEDIATE)

        // Then
        val changeset = fixture.producerChangesets.single() as CompositionChangeset
        assertThat(changeset.changedWindows()).containsExactly(view)
    }

    @Test
    fun `M merge changesets W multiple draw signals coalesce before generation starts`() {
        // Given
        val fixture = Fixture()
        val first = mock<View>()
        val second = mock<View>()
        fixture.orchestrator.start()

        // When
        fixture.orchestrator.requestCapture(CompositionChangeset.of(listOf(first)))
        fixture.orchestrator.requestCapture(CompositionChangeset.of(listOf(second)))
        fixture.scheduler.runNext(IMMEDIATE)

        // Then
        val changeset = fixture.producerChangesets.single() as CompositionChangeset
        assertThat(changeset.changedWindows()).containsExactlyInAnyOrder(first, second)
    }

    @Test
    fun `M keep accumulated changeset W time budget denies admission`() {
        // Given
        val budget = FakeTimeBudget(canStart = false)
        val fixture = Fixture(timeBudget = budget)
        val deniedView = mock<View>()
        val admittedView = mock<View>()
        fixture.orchestrator.start()

        // When
        fixture.orchestrator.requestCapture(CompositionChangeset.of(listOf(deniedView)))
        fixture.scheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fixture.producerCaptures).isZero()

        // When
        budget.canStart = true
        fixture.orchestrator.requestCapture(CompositionChangeset.of(listOf(admittedView)))
        fixture.scheduler.runNext(IMMEDIATE)

        // Then
        val changeset = fixture.producerChangesets.single() as CompositionChangeset
        assertThat(changeset.changedWindows()).containsExactlyInAnyOrder(deniedView, admittedView)
    }

    @Test
    fun `M drop pending changeset W orchestration stops`() {
        // Given
        val fixture = Fixture()
        val view = mock<View>()
        fixture.orchestrator.start()
        fixture.orchestrator.requestCapture(CompositionChangeset.of(listOf(view)))

        // When
        fixture.orchestrator.stop()
        fixture.orchestrator.start()
        fixture.orchestrator.requestCapture()
        fixture.scheduler.runNext(IMMEDIATE)
        fixture.scheduler.runNext(IMMEDIATE)

        // Then
        val changeset = fixture.producerChangesets.single()
        assertThat(changeset.isEmpty()).isTrue()
    }

    @Test
    fun `M cancel generation and ignore callback W processing expires`() {
        // Given
        val fixture = Fixture()
        fixture.orchestrator.start()
        fixture.orchestrator.requestCapture()
        fixture.scheduler.runNext(IMMEDIATE)
        fixture.expiryScheduler.runNext(IMMEDIATE)
        val expiredProcessing = fixture.processor.pending.single()
        fixture.orchestrator.requestCapture()

        // When
        fixture.expiryScheduler.runNext(TIMEOUT_NS)
        fixture.scheduler.runNext(IMMEDIATE)
        fixture.expiryScheduler.runNext(IMMEDIATE)
        expiredProcessing.complete()

        // Then
        assertThat(expiredProcessing.cancelled).isTrue()
        assertThat(fixture.consumed).isEmpty()
        assertThat(fixture.processor.pending.map { it.request.generation.id }).containsExactly(1L, 2L)
    }

    @Test
    fun `M expire generation W completion arrives at deadline before timeout task`() {
        // Given
        val fixture = Fixture()
        fixture.clock.nowNs = 20L
        fixture.orchestrator.start()
        fixture.orchestrator.requestCapture()
        fixture.scheduler.runNext(IMMEDIATE)
        fixture.expiryScheduler.runNext(IMMEDIATE)
        val processing = fixture.processor.pending.single()

        // When
        fixture.clock.nowNs = 20L + TIMEOUT_NS
        processing.complete()

        // Then
        assertThat(processing.cancelled).isTrue()
        assertThat(fixture.consumed).isEmpty()
    }

    @Test
    fun `M hand off generation W completion arrives immediately before deadline`() {
        // Given
        val fixture = Fixture()
        fixture.clock.nowNs = 20L
        fixture.orchestrator.start()
        fixture.orchestrator.requestCapture()
        fixture.scheduler.runNext(IMMEDIATE)
        fixture.expiryScheduler.runNext(IMMEDIATE)
        val processing = fixture.processor.pending.single()

        // When
        fixture.clock.nowNs = 20L + TIMEOUT_NS - 1L
        processing.complete()

        // Then
        assertThat(fixture.consumed).hasSize(1)
    }

    @Test
    fun `M expire generation W completion arrives after deadline`() {
        // Given
        val fixture = Fixture()
        fixture.clock.nowNs = 20L
        fixture.orchestrator.start()
        fixture.orchestrator.requestCapture()
        fixture.scheduler.runNext(IMMEDIATE)
        fixture.expiryScheduler.runNext(IMMEDIATE)
        val processing = fixture.processor.pending.single()

        // When
        fixture.clock.nowNs = 20L + TIMEOUT_NS + 1L
        processing.complete()

        // Then
        assertThat(processing.cancelled).isTrue()
        assertThat(fixture.consumed).isEmpty()
    }

    @Test
    fun `M expose one absolute budget W producer and processor query generation`() {
        // Given
        val fixture = Fixture()
        fixture.clock.nowNs = 20L
        fixture.orchestrator.start()

        // When
        fixture.orchestrator.requestCapture()
        fixture.scheduler.runNext(IMMEDIATE)
        fixture.expiryScheduler.runNext(IMMEDIATE)
        val generation = fixture.producerGenerations.single()
        fixture.clock.nowNs = 50L

        // Then
        assertThat(generation.startedAtNs).isEqualTo(20L)
        assertThat(generation.deadlineNs).isEqualTo(20L + TIMEOUT_NS)
        assertThat(generation.remainingBudgetNs()).isEqualTo(70L)
        assertThat(fixture.processor.pending.single().request.generation).isSameAs(generation)
    }

    @Test
    fun `M invalidate outstanding work token W generation reaches deadline`() {
        // Given
        val fixture = Fixture()
        fixture.orchestrator.start()
        fixture.orchestrator.requestCapture()
        fixture.scheduler.runNext(IMMEDIATE)
        val generation = fixture.producerGenerations.single()
        val token = generation.createWorkToken()

        // When
        fixture.clock.nowNs = TIMEOUT_NS

        // Then
        assertThat(token).isNotNull
        assertThat(token?.isValid()).isFalse()
        assertThat(token?.complete()).isFalse()
        assertThat(generation.remainingBudgetNs()).isZero()
    }

    @Test
    fun `M ignore callback W result carries a different generation identity`() {
        // Given
        val fixture = Fixture()
        fixture.orchestrator.start()
        fixture.orchestrator.requestCapture()
        fixture.scheduler.runNext(IMMEDIATE)
        fixture.expiryScheduler.runNext(IMMEDIATE)
        val processing = fixture.processor.pending.single()

        // When
        processing.complete(generationId = processing.request.generation.id + 1)

        // Then
        assertThat(fixture.consumed).isEmpty()

        // When
        processing.complete()

        // Then
        assertThat(fixture.consumed).hasSize(1)
    }

    @Test
    fun `M ignore stale scheduled capture W pipeline restarts`() {
        // Given
        val fixture = Fixture()
        fixture.orchestrator.start()
        fixture.orchestrator.requestCapture()

        // When
        fixture.orchestrator.stop()
        fixture.orchestrator.start()
        fixture.orchestrator.requestCapture()
        fixture.scheduler.runNext(IMMEDIATE)
        fixture.scheduler.runNext(IMMEDIATE)
        fixture.expiryScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fixture.producerCaptures).isEqualTo(1)
        assertThat(fixture.processor.pending.single().request.generation.id).isEqualTo(1L)
    }

    @Test
    fun `M cancel generation scoped work W orchestration stops`() {
        // Given
        val fixture = Fixture()
        fixture.orchestrator.start()
        fixture.orchestrator.requestCapture()
        fixture.scheduler.runNext(IMMEDIATE)
        fixture.expiryScheduler.runNext(IMMEDIATE)
        val processing = fixture.processor.pending.single()

        // When
        fixture.orchestrator.stop()
        processing.complete()

        // Then
        assertThat(processing.cancelled).isTrue()
        // The expiry timer is deliberately never cancelled - see the fire-and-forget comment in
        // SnapshotCaptureOrchestrator.beginCapture().
        assertThat(fixture.expiryScheduler.tasks.single { it.delayNs == TIMEOUT_NS }.cancelled).isFalse()
        assertThat(fixture.consumed).isEmpty()
    }

    @Test
    fun `M shutdown capture and expiry schedulers W orchestration shuts down`() {
        // Given
        val fixture = Fixture()

        // When
        fixture.orchestrator.shutdown()

        // Then
        assertThat(fixture.scheduler.shutdowns).isEqualTo(1)
        assertThat(fixture.expiryScheduler.shutdowns).isEqualTo(1)
    }

    @Test
    fun `M skip processing and continue W traversal produces no snapshot`() {
        // Given
        val fixture = Fixture(snapshotToProduce = null)
        fixture.orchestrator.start()

        // When
        fixture.orchestrator.requestCapture()
        fixture.scheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fixture.producerCaptures).isEqualTo(1)
        assertThat(fixture.processor.pending).isEmpty()
        assertThat(fixture.consumed).isEmpty()
        // The expiry timer is deliberately never cancelled - see the fire-and-forget comment in
        // SnapshotCaptureOrchestrator.beginCapture().
        assertThat(fixture.expiryScheduler.tasks.single { it.delayNs == TIMEOUT_NS }.cancelled).isFalse()
    }

    @Test
    fun `M skip traversal W capture time budget is exhausted`() {
        // Given
        val budget = FakeTimeBudget(canStart = false)
        val fixture = Fixture(timeBudget = budget)
        fixture.orchestrator.start()

        // When
        fixture.orchestrator.requestCapture()
        fixture.scheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fixture.producerCaptures).isZero()
        assertThat(fixture.producerGenerations).isEmpty()
        assertThat(fixture.consumed).isEmpty()
        assertThat(budget.startChecks).containsExactly(0L)

        // When
        budget.canStart = true
        fixture.orchestrator.requestCapture()
        fixture.scheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fixture.producerCaptures).isEqualTo(1)
        assertThat(fixture.producerGenerations.single().id).isEqualTo(1L)
        assertThat(budget.consumedDurations).containsExactly(0L)
    }

    @Test
    fun `M notify skipped frame W recording time bank denies admission`() {
        // Given
        var skippedFrames = 0
        val deniedTimeBank = object : TimeBank {
            override fun consume(executionTime: Long) = Unit
            override fun updateAndCheck(timestamp: Long): Boolean = false
        }
        val budget = TimeBankCaptureTimeBudget(deniedTimeBank) { skippedFrames++ }

        // When
        val admitted = budget.canStart(0L)

        // Then
        assertThat(admitted).isFalse()
        assertThat(skippedFrames).isEqualTo(1)
    }

    @Test
    fun `M charge active main thread work W capture adapter completes`() {
        // Given
        val budget = FakeTimeBudget(canStart = true)
        val fixture = Fixture(timeBudget = budget, producerExecutionNs = 25L)

        // When
        fixture.orchestrator.start()
        fixture.orchestrator.requestCapture()
        fixture.scheduler.runNext(IMMEDIATE)

        // Then
        assertThat(budget.consumedDurations).containsExactly(25L)
    }

    @Test
    fun `M exclude asynchronous waiting W charge recording time bank`() {
        // Given
        val budget = FakeTimeBudget(canStart = true)
        val fixture = Fixture(timeBudget = budget, producerExecutionNs = 25L)
        fixture.orchestrator.start()
        fixture.orchestrator.requestCapture()
        fixture.scheduler.runNext(IMMEDIATE)
        fixture.expiryScheduler.runNext(IMMEDIATE)

        // When
        fixture.clock.nowNs += 1_000L
        fixture.processor.pending.single().complete()

        // Then
        assertThat(budget.consumedDurations).containsExactly(25L)
    }

    @Test
    fun `M observe cancellation between synchronous capture units W generation expires`() {
        // Given
        val clock = FakeClock()
        val context = CaptureGenerationContext(1L, 0L, 100L, clock)
        var executedUnits = 0

        // When
        val first = context.runMainThreadCaptureUnit {
            executedUnits++
        }
        context.expire()
        val second = context.runMainThreadCaptureUnit {
            executedUnits++
        }

        // Then
        assertThat(first).isInstanceOf(MainThreadCaptureResult.Completed::class.java)
        assertThat(second).isEqualTo(MainThreadCaptureResult.Interrupted)
        assertThat(executedUnits).isEqualTo(1)
    }

    @Test
    fun `M stop further synchronous work W time bank denies continuation`() {
        // Given
        val clock = FakeClock()
        val budget = FakeTimeBudget(canStart = true)
        val context = CaptureGenerationContext(1L, 0L, 100L, clock, budget)
        var executedUnits = 0
        context.runMainThreadCaptureUnit(admissionAlreadyGranted = true) { executedUnits++ }
        budget.canStart = false

        // When
        val result = context.runMainThreadCaptureUnit { executedUnits++ }

        // Then
        assertThat(result).isEqualTo(MainThreadCaptureResult.Interrupted)
        assertThat(executedUnits).isEqualTo(1)
    }

    @Test
    fun `M check deadline before synchronous adapter W deadline already reached`() {
        // Given
        val clock = FakeClock().apply { nowNs = 100L }
        val context = CaptureGenerationContext(1L, 0L, 100L, clock)
        var adapterCalled = false

        // When
        val result = context.runMainThreadCaptureUnit { adapterCalled = true }

        // Then
        assertThat(result).isEqualTo(MainThreadCaptureResult.Interrupted)
        assertThat(adapterCalled).isFalse()
    }

    @Test
    fun `M check deadline after synchronous adapter W deadline reached during call`() {
        // Given
        val clock = FakeClock()
        val context = CaptureGenerationContext(1L, 0L, 100L, clock)

        // When
        val result = context.runMainThreadCaptureUnit {
            clock.nowNs = 100L
        }

        // Then
        assertThat(result).isEqualTo(MainThreadCaptureResult.Interrupted)
        assertThat(context.isActive()).isFalse()
    }

    @Test
    fun `M cooperatively drop capture W blocking adapter outlives deadline`() {
        // Given
        val fixture = Fixture(producerExecutionNs = TIMEOUT_NS)

        // When
        fixture.orchestrator.start()
        fixture.orchestrator.requestCapture()
        fixture.scheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fixture.expiryScheduler.tasks.single().executed).isFalse()
        assertThat(fixture.processor.pending).isEmpty()
        assertThat(fixture.consumed).isEmpty()
    }

    private class Fixture(
        snapshotToProduce: CapturedFullSnapshot? = compositionTestTree().snapshot,
        timeBudget: CaptureTimeBudget = CaptureTimeBudget.UNLIMITED,
        mainThreadExecutor: FakeMainThreadExecutor = FakeMainThreadExecutor(),
        producerExecutionNs: Long = 0L,
        onProducerCapture: () -> Unit = {}
    ) {
        val snapshot = snapshotToProduce
        val clock = FakeClock()
        val scheduler = FakeScheduler()
        val expiryScheduler = FakeScheduler()
        val processor = FakeProcessor()
        val consumed = mutableListOf<CompletedSnapshotCapture>()
        val producerGenerations = mutableListOf<CaptureGenerationContext>()
        val producerChangesets = mutableListOf<CaptureChangeset>()
        var producerCaptures = 0
        val orchestrator = SnapshotCaptureOrchestrator(
            producer = CapturedSnapshotProducer { generation, changeset ->
                producerCaptures++
                producerGenerations += generation
                producerChangesets += changeset
                onProducerCapture()
                clock.nowNs += producerExecutionNs
                snapshotToProduce?.let { CaptureOutput(it, emptyList(), mock()) }
            },
            processor = processor,
            consumer = CompletedSnapshotConsumer(consumed::add),
            timeProvider = clock,
            captureScheduler = scheduler,
            mainThreadExecutor = mainThreadExecutor,
            expiryScheduler = expiryScheduler,
            timeBudget = timeBudget,
            captureDelayNs = IMMEDIATE,
            generationBudgetNs = TIMEOUT_NS
        )
    }

    private class FakeClock : CaptureTimeProvider {
        var nowNs = 0L

        override fun elapsedRealtimeNanos(): Long = nowNs
    }

    private class FakeProcessor : CapturedSnapshotProcessor {
        val pending = mutableListOf<PendingProcessing>()

        override fun process(
            request: SnapshotProcessingRequest,
            callback: SnapshotProcessingCallback
        ): CancellableCaptureWork = PendingProcessing(request, callback).also(pending::add)
    }

    private class FakeMainThreadExecutor(
        private val autoRun: Boolean = true
    ) : CaptureMainThreadExecutor {
        private val tasks = mutableListOf<ScheduledTask>()
        var isExecuting = false
            private set

        override fun execute(task: () -> Unit): CancellableCaptureWork {
            val scheduled = ScheduledTask(IMMEDIATE) {
                isExecuting = true
                try {
                    task()
                } finally {
                    isExecuting = false
                }
            }.also(tasks::add)
            if (autoRun) scheduled.run()
            return scheduled
        }

        fun runNext() {
            tasks.first { !it.executed && !it.cancelled }.run()
        }
    }

    private class PendingProcessing(
        val request: SnapshotProcessingRequest,
        private val callback: SnapshotProcessingCallback
    ) : CancellableCaptureWork {
        var cancelled = false

        override fun cancel() {
            cancelled = true
        }

        fun complete(generationId: Long = request.generation.id) {
            callback.onProcessed(
                SnapshotProcessingResult.Completed(generationId, request.snapshot)
            )
        }
    }

    private class FakeScheduler : CaptureTaskScheduler {
        val tasks = mutableListOf<ScheduledTask>()
        var shutdowns = 0

        override fun schedule(delayNs: Long, task: () -> Unit): CancellableCaptureWork =
            ScheduledTask(delayNs, task).also(tasks::add)

        fun runNext(delayNs: Long) {
            tasks.first { !it.executed && !it.cancelled && it.delayNs == delayNs }.run()
        }

        override fun shutdown() {
            shutdowns++
        }
    }

    private class FakeTimeBudget(
        var canStart: Boolean
    ) : CaptureTimeBudget {
        val startChecks = mutableListOf<Long>()
        val consumedDurations = mutableListOf<Long>()

        override fun canStart(timestampNs: Long): Boolean {
            startChecks += timestampNs
            return canStart
        }

        override fun consume(durationNs: Long) {
            consumedDurations += durationNs
        }
    }

    private class ScheduledTask(
        val delayNs: Long,
        private val task: () -> Unit
    ) : CancellableCaptureWork {
        var cancelled = false
        var executed = false

        override fun cancel() {
            cancelled = true
        }

        fun run() {
            executed = true
            task()
        }
    }

    private companion object {
        const val IMMEDIATE = 0L
        const val TIMEOUT_NS = 100L
    }
}
