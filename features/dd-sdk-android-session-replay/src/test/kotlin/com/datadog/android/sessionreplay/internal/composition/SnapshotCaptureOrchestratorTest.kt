/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.RecordingTimeBank
import com.datadog.android.sessionreplay.internal.recorder.TimeBank
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.kotlin.mock
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SnapshotCaptureOrchestratorTest {

    @Test
    fun `M use 90 ms deadline W generation budget is not overridden`(
        @LongForgery(min = 0L, max = 1_000_000L) fakeStartNs: Long
    ) {
        // Given
        val fakeClock = FakeClock().apply { nowNs = fakeStartNs }
        val fakeCaptureScheduler = FakeScheduler()
        val capturedGenerations = mutableListOf<CaptureGenerationContext>()
        val testedOrchestrator = SnapshotCaptureOrchestrator(
            producer = CapturedSnapshotProducer { context, _ ->
                capturedGenerations += context
                null
            },
            processor = FakeProcessor(),
            consumer = CompletedSnapshotConsumer { _ -> },
            timeProvider = fakeClock,
            captureScheduler = fakeCaptureScheduler,
            mainThreadExecutor = FakeMainThreadExecutor(),
            expiryScheduler = FakeScheduler(),
            captureDelayNs = IMMEDIATE
        )

        // When
        testedOrchestrator.start()
        testedOrchestrator.requestCapture()
        fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        val generation = capturedGenerations.single()
        assertThat(generation.startedAtNs).isEqualTo(fakeStartNs)
        assertThat(generation.deadlineNs - generation.startedAtNs).isEqualTo(DEFAULT_GENERATION_BUDGET_NS)
    }

    @Test
    fun `M keep recording allowance independent W generation deadline is reached`(
        @LongForgery(min = 10L, max = 500L) fakeAllowanceMs: Long,
        @LongForgery(min = 1L, max = 1_000_000L) fakeAllowanceOvershootNs: Long,
        forge: Forge
    ) {
        // Given
        val fakeRecordingTimeBank = RecordingTimeBank(maxTimeBalancePerSecondInMs = fakeAllowanceMs)
        val fakeGeneration = forge.aGenerationContext()

        // When
        val initiallyAdmitted = fakeRecordingTimeBank.updateAndCheck(0L)
        fakeRecordingTimeBank.consume(TimeUnit.MILLISECONDS.toNanos(fakeAllowanceMs) + fakeAllowanceOvershootNs)

        // Then
        assertThat(initiallyAdmitted).isTrue()
        assertThat(fakeRecordingTimeBank.updateAndCheck(0L)).isFalse()
        assertThat(fakeGeneration.remainingBudgetNs()).isEqualTo(fakeGeneration.deadlineNs - fakeGeneration.startedAtNs)
    }

    @Test
    fun `M discover roots on main thread W generation starts`(forge: Forge) {
        // Given
        val fakeMainThreadExecutor = FakeMainThreadExecutor(autoRun = false)
        val fixture = Fixture(forge, mainThreadExecutor = fakeMainThreadExecutor)
        fixture.testedOrchestrator.start()

        // When
        fixture.testedOrchestrator.requestCapture()
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fixture.producerCaptures).isZero()

        // When
        fakeMainThreadExecutor.runNext()

        // Then
        assertThat(fixture.producerCaptures).isEqualTo(1)
    }

    @Test
    fun `M execute synchronous capture adapter on main thread W capture starts`(forge: Forge) {
        // Given
        val fakeMainThreadExecutor = FakeMainThreadExecutor()
        var producerRanOnMainThread = false
        val fixture = Fixture(
            forge,
            mainThreadExecutor = fakeMainThreadExecutor,
            onProducerCapture = { producerRanOnMainThread = fakeMainThreadExecutor.isExecuting }
        )

        // When
        fixture.testedOrchestrator.start()
        fixture.testedOrchestrator.requestCapture()
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(producerRanOnMainThread).isTrue()
    }

    @Test
    fun `M release main thread W generation waits for asynchronous processing`(forge: Forge) {
        // Given
        val fakeMainThreadExecutor = FakeMainThreadExecutor()
        val fixture = Fixture(forge, mainThreadExecutor = fakeMainThreadExecutor)
        fixture.testedOrchestrator.start()

        // When
        fixture.testedOrchestrator.requestCapture()
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fixture.fakeProcessor.pending).hasSize(1)
        assertThat(fakeMainThreadExecutor.isExecuting).isFalse()
        assertThat(fixture.consumedCaptures).isEmpty()
    }

    @Test
    fun `M hand off completed snapshot W processing completes in generation`(forge: Forge) {
        // Given
        val fixture = Fixture(forge)

        // When
        fixture.testedOrchestrator.start()
        fixture.testedOrchestrator.requestCapture()
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)
        val processing = fixture.fakeProcessor.pending.single()
        processing.complete()

        // Then
        assertThat(fixture.producerCaptures).isEqualTo(1)
        assertThat(fixture.consumedCaptures).hasSize(1)
        assertThat(fixture.consumedCaptures.single().generation).isEqualTo(processing.request.generation)
        assertThat(fixture.consumedCaptures.single().snapshot).isSameAs(fixture.fakeSnapshot)
        assertThat(fixture.expiryTask().cancelled).isFalse()
    }

    @Test
    fun `M coalesce a follow-up capture W draw signals arrive during processing`(forge: Forge) {
        // Given
        val fixture = Fixture(forge)
        fixture.testedOrchestrator.start()
        fixture.testedOrchestrator.requestCapture()
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // When
        fixture.testedOrchestrator.requestCapture()
        fixture.testedOrchestrator.requestCapture()
        fixture.fakeProcessor.pending.single().complete()
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fixture.producerCaptures).isEqualTo(2)
        assertThat(fixture.fakeProcessor.pending).hasSize(2)
        assertThat(fixture.fakeProcessor.pending.map { it.request.generation.id }).containsExactly(1L, 2L)
    }

    @Test
    fun `M pass changeset to producer W requestCapture supplies one`(forge: Forge) {
        // Given
        val fixture = Fixture(forge)
        val fakeView = mock<View>()
        fixture.testedOrchestrator.start()

        // When
        fixture.testedOrchestrator.requestCapture(CompositionChangeset.of(listOf(fakeView)))
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        val changeset = fixture.producerChangesets.single() as CompositionChangeset
        assertThat(changeset.changedWindows()).containsExactly(fakeView)
    }

    @Test
    fun `M merge changesets W multiple draw signals coalesce before generation starts`(forge: Forge) {
        // Given
        val fixture = Fixture(forge)
        val fakeFirstView = mock<View>()
        val fakeSecondView = mock<View>()
        fixture.testedOrchestrator.start()

        // When
        fixture.testedOrchestrator.requestCapture(CompositionChangeset.of(listOf(fakeFirstView)))
        fixture.testedOrchestrator.requestCapture(CompositionChangeset.of(listOf(fakeSecondView)))
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        val changeset = fixture.producerChangesets.single() as CompositionChangeset
        assertThat(changeset.changedWindows()).containsExactlyInAnyOrder(fakeFirstView, fakeSecondView)
    }

    @Test
    fun `M keep accumulated changeset W time budget denies admission`(forge: Forge) {
        // Given
        val fakeTimeBudget = FakeTimeBudget(canStart = false)
        val fixture = Fixture(forge, timeBudget = fakeTimeBudget)
        val fakeDeniedView = mock<View>()
        val fakeAdmittedView = mock<View>()
        fixture.testedOrchestrator.start()

        // When
        fixture.testedOrchestrator.requestCapture(CompositionChangeset.of(listOf(fakeDeniedView)))
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fixture.producerCaptures).isZero()

        // When
        fakeTimeBudget.canStart = true
        fixture.testedOrchestrator.requestCapture(CompositionChangeset.of(listOf(fakeAdmittedView)))
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        val changeset = fixture.producerChangesets.single() as CompositionChangeset
        assertThat(changeset.changedWindows()).containsExactlyInAnyOrder(fakeDeniedView, fakeAdmittedView)
    }

    @Test
    fun `M drop pending changeset W orchestration stops`(forge: Forge) {
        // Given
        val fixture = Fixture(forge)
        val fakeView = mock<View>()
        fixture.testedOrchestrator.start()
        fixture.testedOrchestrator.requestCapture(CompositionChangeset.of(listOf(fakeView)))

        // When
        fixture.testedOrchestrator.stop()
        fixture.testedOrchestrator.start()
        fixture.testedOrchestrator.requestCapture()
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fixture.producerChangesets.single().isEmpty()).isTrue()
    }

    @Test
    fun `M cancel generation and ignore callback W processing expires`(forge: Forge) {
        // Given
        val fixture = Fixture(forge)
        fixture.testedOrchestrator.start()
        fixture.testedOrchestrator.requestCapture()
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)
        val expiredProcessing = fixture.fakeProcessor.pending.single()
        fixture.testedOrchestrator.requestCapture()

        // When
        fixture.fakeExpiryScheduler.runNext(fixture.fakeGenerationBudgetNs)
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)
        expiredProcessing.complete()

        // Then
        assertThat(expiredProcessing.cancelled).isTrue()
        assertThat(fixture.consumedCaptures).isEmpty()
        assertThat(fixture.fakeProcessor.pending.map { it.request.generation.id }).containsExactly(1L, 2L)
    }

    @Test
    fun `M expire generation W completion arrives at deadline before timeout task`(forge: Forge) {
        // Given
        val fixture = Fixture(forge)
        fixture.testedOrchestrator.start()
        fixture.testedOrchestrator.requestCapture()
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)
        val processing = fixture.fakeProcessor.pending.single()

        // When
        fixture.fakeClock.nowNs = processing.request.generation.deadlineNs
        processing.complete()

        // Then
        assertThat(processing.cancelled).isTrue()
        assertThat(fixture.consumedCaptures).isEmpty()
    }

    @Test
    fun `M hand off generation W completion arrives immediately before deadline`(forge: Forge) {
        // Given
        val fixture = Fixture(forge)
        fixture.testedOrchestrator.start()
        fixture.testedOrchestrator.requestCapture()
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)
        val processing = fixture.fakeProcessor.pending.single()

        // When
        fixture.fakeClock.nowNs = processing.request.generation.deadlineNs - 1L
        processing.complete()

        // Then
        assertThat(fixture.consumedCaptures).hasSize(1)
    }

    @Test
    fun `M expire generation W completion arrives after deadline`(
        @LongForgery(min = 1L, max = 10_000L) fakeOverrunNs: Long,
        forge: Forge
    ) {
        // Given
        val fixture = Fixture(forge)
        fixture.testedOrchestrator.start()
        fixture.testedOrchestrator.requestCapture()
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)
        val processing = fixture.fakeProcessor.pending.single()

        // When
        fixture.fakeClock.nowNs = processing.request.generation.deadlineNs + fakeOverrunNs
        processing.complete()

        // Then
        assertThat(processing.cancelled).isTrue()
        assertThat(fixture.consumedCaptures).isEmpty()
    }

    @Test
    fun `M expose one absolute budget W producer and processor query generation`(forge: Forge) {
        // Given
        val fixture = Fixture(forge)
        val fakeElapsedNs = forge.aLong(min = 1L, max = fixture.fakeGenerationBudgetNs)
        fixture.testedOrchestrator.start()

        // When
        fixture.testedOrchestrator.requestCapture()
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)
        val generation = fixture.producerGenerations.single()
        fixture.fakeClock.nowNs = fixture.fakeStartNs + fakeElapsedNs

        // Then
        assertThat(generation.startedAtNs).isEqualTo(fixture.fakeStartNs)
        assertThat(generation.deadlineNs).isEqualTo(fixture.fakeStartNs + fixture.fakeGenerationBudgetNs)
        assertThat(generation.remainingBudgetNs()).isEqualTo(fixture.fakeGenerationBudgetNs - fakeElapsedNs)
        assertThat(fixture.fakeProcessor.pending.single().request.generation).isSameAs(generation)
    }

    @Test
    fun `M invalidate outstanding work token W generation reaches deadline`(forge: Forge) {
        // Given
        val fixture = Fixture(forge)
        fixture.testedOrchestrator.start()
        fixture.testedOrchestrator.requestCapture()
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)
        val generation = fixture.producerGenerations.single()
        val token = generation.createWorkToken()

        // When
        fixture.fakeClock.nowNs = generation.deadlineNs

        // Then
        assertThat(token).isNotNull
        assertThat(token?.isValid()).isFalse()
        assertThat(token?.complete()).isFalse()
        assertThat(generation.remainingBudgetNs()).isZero()
    }

    @Test
    fun `M ignore callback W result carries a different generation identity`(
        @LongForgery(min = 1L, max = 100L) fakeIdOffset: Long,
        forge: Forge
    ) {
        // Given
        val fixture = Fixture(forge)
        fixture.testedOrchestrator.start()
        fixture.testedOrchestrator.requestCapture()
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)
        val processing = fixture.fakeProcessor.pending.single()

        // When
        processing.complete(generationId = processing.request.generation.id + fakeIdOffset)

        // Then
        assertThat(fixture.consumedCaptures).isEmpty()

        // When
        processing.complete()

        // Then
        assertThat(fixture.consumedCaptures).hasSize(1)
    }

    @Test
    fun `M ignore stale scheduled capture W pipeline restarts`(forge: Forge) {
        // Given
        val fixture = Fixture(forge)
        fixture.testedOrchestrator.start()
        fixture.testedOrchestrator.requestCapture()

        // When
        fixture.testedOrchestrator.stop()
        fixture.testedOrchestrator.start()
        fixture.testedOrchestrator.requestCapture()
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fixture.producerCaptures).isEqualTo(1)
        assertThat(fixture.fakeProcessor.pending.single().request.generation.id).isEqualTo(1L)
    }

    @Test
    fun `M cancel generation scoped work W orchestration stops`(forge: Forge) {
        // Given
        val fixture = Fixture(forge)
        fixture.testedOrchestrator.start()
        fixture.testedOrchestrator.requestCapture()
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)
        val processing = fixture.fakeProcessor.pending.single()

        // When
        fixture.testedOrchestrator.stop()
        processing.complete()

        // Then
        assertThat(processing.cancelled).isTrue()
        assertThat(fixture.expiryTask().cancelled).isTrue()
        assertThat(fixture.consumedCaptures).isEmpty()
    }

    @Test
    fun `M shutdown capture and expiry schedulers W orchestration shuts down`(forge: Forge) {
        // Given
        val fixture = Fixture(forge)

        // When
        fixture.testedOrchestrator.shutdown()

        // Then
        assertThat(fixture.fakeCaptureScheduler.shutdowns).isEqualTo(1)
        assertThat(fixture.fakeExpiryScheduler.shutdowns).isEqualTo(1)
    }

    @Test
    fun `M skip processing and continue W traversal produces no snapshot`(forge: Forge) {
        // Given
        val fixture = Fixture(forge, snapshotToProduce = null)
        fixture.testedOrchestrator.start()

        // When
        fixture.testedOrchestrator.requestCapture()
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fixture.producerCaptures).isEqualTo(1)
        assertThat(fixture.fakeProcessor.pending).isEmpty()
        assertThat(fixture.consumedCaptures).isEmpty()
        assertThat(fixture.expiryTask().cancelled).isTrue()
    }

    @Test
    fun `M skip traversal W capture time budget is exhausted`(forge: Forge) {
        // Given
        val fakeTimeBudget = FakeTimeBudget(canStart = false)
        val fixture = Fixture(forge, timeBudget = fakeTimeBudget)
        fixture.testedOrchestrator.start()

        // When
        fixture.testedOrchestrator.requestCapture()
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fixture.producerCaptures).isZero()
        assertThat(fixture.producerGenerations).isEmpty()
        assertThat(fixture.consumedCaptures).isEmpty()
        assertThat(fakeTimeBudget.startChecks).containsExactly(fixture.fakeStartNs)

        // When
        fakeTimeBudget.canStart = true
        fixture.testedOrchestrator.requestCapture()
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fixture.producerCaptures).isEqualTo(1)
        assertThat(fixture.producerGenerations.single().id).isEqualTo(1L)
        assertThat(fakeTimeBudget.consumedDurations).containsExactly(0L)
    }

    @Test
    fun `M notify skipped frame W recording time bank denies admission`(
        @LongForgery(min = 0L, max = 1_000_000L) fakeTimestampNs: Long
    ) {
        // Given
        var skippedFrames = 0
        val stubDeniedTimeBank = object : TimeBank {
            override fun consume(executionTime: Long) = Unit
            override fun updateAndCheck(timestamp: Long): Boolean = false
        }
        val testedTimeBudget = TimeBankCaptureTimeBudget(stubDeniedTimeBank) { skippedFrames++ }

        // When
        val admitted = testedTimeBudget.canStart(fakeTimestampNs)

        // Then
        assertThat(admitted).isFalse()
        assertThat(skippedFrames).isEqualTo(1)
    }

    @Test
    fun `M charge active main thread work W capture adapter completes`(
        @LongForgery(min = 1L, max = 1_000L) fakeProducerExecutionNs: Long,
        forge: Forge
    ) {
        // Given
        val fakeTimeBudget = FakeTimeBudget(canStart = true)
        val fixture = Fixture(
            forge,
            timeBudget = fakeTimeBudget,
            producerExecutionNs = fakeProducerExecutionNs
        )

        // When
        fixture.testedOrchestrator.start()
        fixture.testedOrchestrator.requestCapture()
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fakeTimeBudget.consumedDurations).containsExactly(fakeProducerExecutionNs)
    }

    @Test
    fun `M exclude asynchronous waiting W charge recording time bank`(
        @LongForgery(min = 1L, max = 1_000L) fakeProducerExecutionNs: Long,
        @LongForgery(min = 1L, max = 1_000L) fakeAsynchronousWaitNs: Long,
        forge: Forge
    ) {
        // Given
        val fakeTimeBudget = FakeTimeBudget(canStart = true)
        val fixture = Fixture(
            forge,
            timeBudget = fakeTimeBudget,
            producerExecutionNs = fakeProducerExecutionNs
        )
        fixture.testedOrchestrator.start()
        fixture.testedOrchestrator.requestCapture()
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // When
        fixture.fakeClock.nowNs += fakeAsynchronousWaitNs
        fixture.fakeProcessor.pending.single().complete()

        // Then
        assertThat(fakeTimeBudget.consumedDurations).containsExactly(fakeProducerExecutionNs)
    }

    @Test
    fun `M observe cancellation between synchronous capture units W generation expires`(forge: Forge) {
        // Given
        val testedGeneration = forge.aGenerationContext()
        var executedUnits = 0

        // When
        val first = testedGeneration.runMainThreadCaptureUnit { executedUnits++ }
        testedGeneration.expire()
        val second = testedGeneration.runMainThreadCaptureUnit { executedUnits++ }

        // Then
        assertThat(first).isInstanceOf(MainThreadCaptureResult.Completed::class.java)
        assertThat(second).isEqualTo(MainThreadCaptureResult.Interrupted)
        assertThat(executedUnits).isEqualTo(1)
    }

    @Test
    fun `M stop further synchronous work W time bank denies continuation`(forge: Forge) {
        // Given
        val fakeTimeBudget = FakeTimeBudget(canStart = true)
        val testedGeneration = forge.aGenerationContext(mainThreadTimeBudget = fakeTimeBudget)
        var executedUnits = 0
        testedGeneration.runMainThreadCaptureUnit(admissionAlreadyGranted = true) { executedUnits++ }
        fakeTimeBudget.canStart = false

        // When
        val result = testedGeneration.runMainThreadCaptureUnit { executedUnits++ }

        // Then
        assertThat(result).isEqualTo(MainThreadCaptureResult.Interrupted)
        assertThat(executedUnits).isEqualTo(1)
    }

    @Test
    fun `M check deadline before synchronous adapter W deadline already reached`(forge: Forge) {
        // Given
        val fakeClock = FakeClock()
        val testedGeneration = forge.aGenerationContext(clock = fakeClock)
        fakeClock.nowNs = testedGeneration.deadlineNs
        var adapterCalled = false

        // When
        val result = testedGeneration.runMainThreadCaptureUnit { adapterCalled = true }

        // Then
        assertThat(result).isEqualTo(MainThreadCaptureResult.Interrupted)
        assertThat(adapterCalled).isFalse()
    }

    @Test
    fun `M check deadline after synchronous adapter W deadline reached during call`(forge: Forge) {
        // Given
        val fakeClock = FakeClock()
        val testedGeneration = forge.aGenerationContext(clock = fakeClock)

        // When
        val result = testedGeneration.runMainThreadCaptureUnit {
            fakeClock.nowNs = testedGeneration.deadlineNs
        }

        // Then
        assertThat(result).isEqualTo(MainThreadCaptureResult.Interrupted)
        assertThat(testedGeneration.isActive()).isFalse()
    }

    @Test
    fun `M cooperatively drop capture W blocking adapter outlives deadline`(forge: Forge) {
        // Given
        val fakeGenerationBudgetNs = forge.aGenerationBudgetNs()
        val fixture = Fixture(
            forge,
            fakeGenerationBudgetNs = fakeGenerationBudgetNs,
            producerExecutionNs = fakeGenerationBudgetNs
        )

        // When
        fixture.testedOrchestrator.start()
        fixture.testedOrchestrator.requestCapture()
        fixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fixture.fakeExpiryScheduler.tasks.single().executed).isFalse()
        assertThat(fixture.fakeProcessor.pending).isEmpty()
        assertThat(fixture.consumedCaptures).isEmpty()
    }

    private class Fixture(
        forge: Forge,
        snapshotToProduce: CapturedFullSnapshot? = forge.aCompositionTestTree().snapshot,
        timeBudget: CaptureTimeBudget = CaptureTimeBudget.UNLIMITED,
        mainThreadExecutor: FakeMainThreadExecutor = FakeMainThreadExecutor(),
        producerExecutionNs: Long = 0L,
        val fakeStartNs: Long = forge.aLong(min = 0L, max = 1_000_000L),
        val fakeGenerationBudgetNs: Long = forge.aGenerationBudgetNs(),
        onProducerCapture: () -> Unit = {}
    ) {
        val fakeSnapshot = snapshotToProduce
        val fakeClock = FakeClock().apply { nowNs = fakeStartNs }
        val fakeCaptureScheduler = FakeScheduler()
        val fakeExpiryScheduler = FakeScheduler()
        val fakeProcessor = FakeProcessor()
        val consumedCaptures = mutableListOf<CompletedSnapshotCapture>()
        val producerGenerations = mutableListOf<CaptureGenerationContext>()
        val producerChangesets = mutableListOf<CaptureChangeset>()
        var producerCaptures = 0
        val testedOrchestrator = SnapshotCaptureOrchestrator(
            producer = CapturedSnapshotProducer { generation, changeset ->
                producerCaptures++
                producerGenerations += generation
                producerChangesets += changeset
                onProducerCapture()
                fakeClock.nowNs += producerExecutionNs
                snapshotToProduce
            },
            processor = fakeProcessor,
            consumer = CompletedSnapshotConsumer(consumedCaptures::add),
            timeProvider = fakeClock,
            captureScheduler = fakeCaptureScheduler,
            mainThreadExecutor = mainThreadExecutor,
            expiryScheduler = fakeExpiryScheduler,
            timeBudget = timeBudget,
            captureDelayNs = IMMEDIATE,
            generationBudgetNs = fakeGenerationBudgetNs
        )

        fun expiryTask(): ScheduledTask = fakeExpiryScheduler.tasks.single { it.delayNs == fakeGenerationBudgetNs }
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
        val DEFAULT_GENERATION_BUDGET_NS = TimeUnit.MILLISECONDS.toNanos(90)

        fun Forge.aGenerationBudgetNs(): Long = aLong(min = 1_000L, max = 10_000_000L)

        fun Forge.aGenerationContext(
            clock: FakeClock = FakeClock(),
            mainThreadTimeBudget: CaptureTimeBudget = CaptureTimeBudget.UNLIMITED
        ): CaptureGenerationContext {
            val startedAtNs = aLong(min = 0L, max = 1_000_000L)
            clock.nowNs = startedAtNs
            return CaptureGenerationContext(
                id = aLong(min = 1L, max = 1_000L),
                startedAtNs = startedAtNs,
                deadlineNs = startedAtNs + aGenerationBudgetNs(),
                timeProvider = clock,
                mainThreadTimeBudget = mainThreadTimeBudget
            )
        }
    }
}
