/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.RecordingTimeBank
import com.datadog.android.sessionreplay.internal.recorder.TimeBank
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SnapshotCaptureOrchestratorTest {

    @Test
    fun `M use 90 ms deadline W requestCapture() { generation budget is not overridden }`(
        @LongForgery(min = 0L, max = 1_000_000L) fakeStartNs: Long
    ) {
        // Given
        val fakeClock = FakeClock().apply { nowNs = fakeStartNs }
        val fakeCaptureScheduler = FakeScheduler()
        val fakeCapturedGenerations = mutableListOf<CaptureGenerationContext>()
        val testedOrchestrator = SnapshotCaptureOrchestrator(
            producer = CapturedSnapshotProducer { context ->
                fakeCapturedGenerations += context
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
        val generation = fakeCapturedGenerations.single()
        assertThat(generation.startedAtNs).isEqualTo(fakeStartNs)
        assertThat(generation.deadlineNs - generation.startedAtNs).isEqualTo(DEFAULT_GENERATION_BUDGET_NS)
    }

    @Test
    fun `M keep recording allowance independent W updateAndCheck() + consume() { generation deadline is reached }`(
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
    fun `M defer root discovery W requestCapture() { main-thread task queued }`(forge: Forge) {
        // Given
        val fakeMainThreadExecutor = FakeMainThreadExecutor(autoRun = false)
        val fakeFixture = Fixture(forge, mainThreadExecutor = fakeMainThreadExecutor)
        fakeFixture.testedOrchestrator.start()

        // When
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fakeFixture.producerCaptures).isZero()
    }

    @Test
    fun `M discover roots W runNext() { posted main-thread task executes }`(forge: Forge) {
        // Given
        val fakeMainThreadExecutor = FakeMainThreadExecutor(autoRun = false)
        val fakeFixture = Fixture(forge, mainThreadExecutor = fakeMainThreadExecutor)
        fakeFixture.testedOrchestrator.start()

        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // When
        fakeMainThreadExecutor.runNext()

        // Then
        assertThat(fakeFixture.producerCaptures).isEqualTo(1)
    }

    @Test
    fun `M execute synchronous capture adapter on main thread W requestCapture() { capture starts }`(forge: Forge) {
        // Given
        val fakeMainThreadExecutor = FakeMainThreadExecutor()
        var fakeProducerRanOnMainThread = false
        val fakeFixture = Fixture(
            forge,
            mainThreadExecutor = fakeMainThreadExecutor,
            onProducerCapture = { fakeProducerRanOnMainThread = fakeMainThreadExecutor.isExecuting }
        )

        // When
        fakeFixture.testedOrchestrator.start()
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fakeProducerRanOnMainThread).isTrue()
    }

    @Test
    fun `M release main thread W requestCapture() { generation waits for asynchronous processing }`(forge: Forge) {
        // Given
        val fakeMainThreadExecutor = FakeMainThreadExecutor()
        val fakeFixture = Fixture(forge, mainThreadExecutor = fakeMainThreadExecutor)
        fakeFixture.testedOrchestrator.start()

        // When
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fakeFixture.fakeProcessor.pending).hasSize(1)
        assertThat(fakeMainThreadExecutor.isExecuting).isFalse()
        assertThat(fakeFixture.consumedCaptures).isEmpty()
    }

    @Test
    fun `M hand off completed snapshot W complete() { active generation }`(forge: Forge) {
        // Given
        val fakeFixture = Fixture(forge)

        // When
        fakeFixture.testedOrchestrator.start()
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)
        val processing = fakeFixture.fakeProcessor.pending.single()
        processing.complete()

        // Then
        assertThat(fakeFixture.producerCaptures).isEqualTo(1)
        assertThat(fakeFixture.consumedCaptures).hasSize(1)
        assertThat(fakeFixture.consumedCaptures.single().generation).isEqualTo(processing.request.generation)
        assertThat(fakeFixture.consumedCaptures.single().snapshot).isSameAs(fakeFixture.fakeSnapshot)
        assertThat(fakeFixture.expiryTask().cancelled).isFalse()
    }

    @Test
    fun `M coalesce a follow-up capture W requestCapture() { draw signals arrive during processing }`(forge: Forge) {
        // Given
        val fakeFixture = Fixture(forge)
        fakeFixture.testedOrchestrator.start()
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // When
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeProcessor.pending.single().complete()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fakeFixture.producerCaptures).isEqualTo(2)
        assertThat(fakeFixture.fakeProcessor.pending).hasSize(2)
        assertThat(fakeFixture.fakeProcessor.pending.map { it.request.generation.id }).containsExactly(1L, 2L)
    }

    @Test
    fun `M cancel expired processing W runNext() { generation timeout }`(forge: Forge) {
        // Given
        val fakeFixture = Fixture(forge)
        fakeFixture.testedOrchestrator.start()
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)
        val fakeExpiredProcessing = fakeFixture.fakeProcessor.pending.single()
        fakeFixture.testedOrchestrator.requestCapture()

        // When
        fakeFixture.fakeExpiryScheduler.runNext(fakeFixture.fakeGenerationBudgetNs)

        // Then
        assertThat(fakeExpiredProcessing.cancelled).isTrue()
    }

    @Test
    fun `M ignore expired completion W complete() { newer generation processing }`(forge: Forge) {
        // Given
        val fakeFixture = Fixture(forge)
        fakeFixture.testedOrchestrator.start()
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)
        val fakeExpiredProcessing = fakeFixture.fakeProcessor.pending.single()
        fakeFixture.testedOrchestrator.requestCapture()

        fakeFixture.fakeExpiryScheduler.runNext(fakeFixture.fakeGenerationBudgetNs)
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // When
        fakeExpiredProcessing.complete()

        // Then
        assertThat(fakeFixture.consumedCaptures).isEmpty()
    }

    @Test
    fun `M admit pending capture W runNext() { preceding generation expired }`(forge: Forge) {
        // Given
        val fakeFixture = Fixture(forge)
        fakeFixture.testedOrchestrator.start()
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)
        fakeFixture.testedOrchestrator.requestCapture()

        // When
        fakeFixture.fakeExpiryScheduler.runNext(fakeFixture.fakeGenerationBudgetNs)
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fakeFixture.fakeProcessor.pending.map { it.request.generation.id }).containsExactly(1L, 2L)
    }

    @Test
    fun `M expire generation W complete() { completion arrives at deadline before timeout task }`(forge: Forge) {
        // Given
        val fakeFixture = Fixture(forge)
        fakeFixture.testedOrchestrator.start()
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)
        val fakeProcessing = fakeFixture.fakeProcessor.pending.single()

        // When
        fakeFixture.fakeClock.nowNs = fakeProcessing.request.generation.deadlineNs
        fakeProcessing.complete()

        // Then
        assertThat(fakeProcessing.cancelled).isTrue()
        assertThat(fakeFixture.consumedCaptures).isEmpty()
    }

    @Test
    fun `M hand off generation W complete() { completion arrives immediately before deadline }`(forge: Forge) {
        // Given
        val fakeFixture = Fixture(forge)
        fakeFixture.testedOrchestrator.start()
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)
        val fakeProcessing = fakeFixture.fakeProcessor.pending.single()

        // When
        fakeFixture.fakeClock.nowNs = fakeProcessing.request.generation.deadlineNs - 1L
        fakeProcessing.complete()

        // Then
        assertThat(fakeFixture.consumedCaptures).hasSize(1)
    }

    @Test
    fun `M expire generation W complete() { completion arrives after deadline }`(
        @LongForgery(min = 1L, max = 10_000L) fakeOverrunNs: Long,
        forge: Forge
    ) {
        // Given
        val fakeFixture = Fixture(forge)
        fakeFixture.testedOrchestrator.start()
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)
        val fakeProcessing = fakeFixture.fakeProcessor.pending.single()

        // When
        fakeFixture.fakeClock.nowNs = fakeProcessing.request.generation.deadlineNs + fakeOverrunNs
        fakeProcessing.complete()

        // Then
        assertThat(fakeProcessing.cancelled).isTrue()
        assertThat(fakeFixture.consumedCaptures).isEmpty()
    }

    @Test
    fun `M expose one absolute budget W requestCapture() { producer and processor query generation }`(forge: Forge) {
        // Given
        val fakeFixture = Fixture(forge)
        val fakeElapsedNs = forge.aLong(min = 1L, max = fakeFixture.fakeGenerationBudgetNs)
        fakeFixture.testedOrchestrator.start()

        // When
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)
        val generation = fakeFixture.producerGenerations.single()
        fakeFixture.fakeClock.nowNs = fakeFixture.fakeStartNs + fakeElapsedNs

        // Then
        assertThat(generation.startedAtNs).isEqualTo(fakeFixture.fakeStartNs)
        assertThat(generation.deadlineNs).isEqualTo(fakeFixture.fakeStartNs + fakeFixture.fakeGenerationBudgetNs)
        assertThat(generation.remainingBudgetNs()).isEqualTo(fakeFixture.fakeGenerationBudgetNs - fakeElapsedNs)
        assertThat(fakeFixture.fakeProcessor.pending.single().request.generation).isSameAs(generation)
    }

    @Test
    fun `M invalidate outstanding work token W isValid() + complete() { generation deadline reached }`(forge: Forge) {
        // Given
        val fakeFixture = Fixture(forge)
        fakeFixture.testedOrchestrator.start()
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)
        val fakeGeneration = fakeFixture.producerGenerations.single()
        val testedToken = fakeGeneration.createWorkToken()
        fakeFixture.fakeClock.nowNs = fakeGeneration.deadlineNs

        // When
        val valid = testedToken?.isValid()
        val completed = testedToken?.complete()
        val remainingBudgetNs = fakeGeneration.remainingBudgetNs()

        // Then
        checkNotNull(testedToken) { "Expected an outstanding token created before the deadline" }
        assertThat(valid).isFalse()
        assertThat(completed).isFalse()
        assertThat(remainingBudgetNs).isZero()
    }

    @Test
    fun `M ignore callback W complete() { result carries a different generation identity }`(
        @LongForgery(min = 1L, max = 100L) fakeIdOffset: Long,
        forge: Forge
    ) {
        // Given
        val fakeFixture = Fixture(forge)
        fakeFixture.testedOrchestrator.start()
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)
        val fakeProcessing = fakeFixture.fakeProcessor.pending.single()

        // When
        fakeProcessing.complete(generationId = fakeProcessing.request.generation.id + fakeIdOffset)

        // Then
        assertThat(fakeFixture.consumedCaptures).isEmpty()
    }

    @Test
    fun `M accept matching completion W complete() { mismatched completion ignored earlier }`(
        @LongForgery(min = 1L, max = 100L) fakeIdOffset: Long,
        forge: Forge
    ) {
        // Given
        val fakeFixture = Fixture(forge)
        fakeFixture.testedOrchestrator.start()
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)
        val fakeProcessing = fakeFixture.fakeProcessor.pending.single()

        fakeProcessing.complete(generationId = fakeProcessing.request.generation.id + fakeIdOffset)

        // When
        fakeProcessing.complete()

        // Then
        assertThat(fakeFixture.consumedCaptures).hasSize(1)
    }

    @Test
    fun `M ignore stale scheduled capture W stop() + start() + requestCapture() { pipeline restarts }`(forge: Forge) {
        // Given
        val fakeFixture = Fixture(forge)
        fakeFixture.testedOrchestrator.start()
        fakeFixture.testedOrchestrator.requestCapture()

        // When
        fakeFixture.testedOrchestrator.stop()
        fakeFixture.testedOrchestrator.start()
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fakeFixture.producerCaptures).isEqualTo(1)
        assertThat(fakeFixture.fakeProcessor.pending.single().request.generation.id).isEqualTo(1L)
    }

    @Test
    fun `M cancel generation scoped work W stop() { orchestration stops }`(forge: Forge) {
        // Given
        val fakeFixture = Fixture(forge)
        fakeFixture.testedOrchestrator.start()
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)
        val fakeProcessing = fakeFixture.fakeProcessor.pending.single()

        // When
        fakeFixture.testedOrchestrator.stop()

        // Then
        assertThat(fakeProcessing.cancelled).isTrue()
        assertThat(fakeFixture.expiryTask().cancelled).isTrue()
    }

    @Test
    fun `M ignore stopped generation completion W complete()`(forge: Forge) {
        // Given
        val fakeFixture = Fixture(forge)
        fakeFixture.testedOrchestrator.start()
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)
        val fakeProcessing = fakeFixture.fakeProcessor.pending.single()

        fakeFixture.testedOrchestrator.stop()

        // When
        fakeProcessing.complete()

        // Then
        assertThat(fakeFixture.consumedCaptures).isEmpty()
    }

    @Test
    fun `M shutdown capture and expiry schedulers W shutdown() { orchestration shuts down }`(forge: Forge) {
        // Given
        val fakeFixture = Fixture(forge)

        // When
        fakeFixture.testedOrchestrator.shutdown()

        // Then
        assertThat(fakeFixture.fakeCaptureScheduler.shutdowns).isEqualTo(1)
        assertThat(fakeFixture.fakeExpiryScheduler.shutdowns).isEqualTo(1)
    }

    @Test
    fun `M discard empty capture W requestCapture() { traversal produces no snapshot }`(forge: Forge) {
        // Given
        val fakeFixture = Fixture(forge, snapshotToProduce = null)
        fakeFixture.testedOrchestrator.start()

        // When
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fakeFixture.producerCaptures).isEqualTo(1)
        assertThat(fakeFixture.fakeProcessor.pending).isEmpty()
        assertThat(fakeFixture.consumedCaptures).isEmpty()
        assertThat(fakeFixture.expiryTask().cancelled).isTrue()
    }

    @Test
    fun `M skip traversal W requestCapture() { capture time budget is exhausted }`(forge: Forge) {
        // Given
        val fakeTimeBudget = FakeTimeBudget(canStart = false)
        val fakeFixture = Fixture(forge, timeBudget = fakeTimeBudget)
        fakeFixture.testedOrchestrator.start()

        // When
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fakeFixture.producerCaptures).isZero()
        assertThat(fakeFixture.producerGenerations).isEmpty()
        assertThat(fakeFixture.consumedCaptures).isEmpty()
        assertThat(fakeTimeBudget.startChecks).containsExactly(fakeFixture.fakeStartNs)
    }

    @Test
    fun `M admit a fresh request W requestCapture() { budget recovered after denial }`(forge: Forge) {
        // Given
        val fakeTimeBudget = FakeTimeBudget(canStart = false)
        val fakeFixture = Fixture(forge, timeBudget = fakeTimeBudget)
        fakeFixture.testedOrchestrator.start()

        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        fakeTimeBudget.canStart = true

        // When
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fakeFixture.producerCaptures).isEqualTo(1)
        assertThat(fakeFixture.producerGenerations.single().id).isEqualTo(1L)
        assertThat(fakeTimeBudget.consumedDurations).containsExactly(0L)
    }

    @Test
    fun `M retry without a new request W runNext() { time budget recovers }`(forge: Forge) {
        // Given
        val fakeTimeBudget = FakeTimeBudget(canStart = false)
        val fakeFixture = Fixture(forge, timeBudget = fakeTimeBudget)
        fakeFixture.testedOrchestrator.start()

        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // When
        fakeTimeBudget.canStart = true
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fakeFixture.producerCaptures).isEqualTo(1)
    }

    @Test
    fun `M forward admission check to time bank W canStart()`(
        @LongForgery(min = 0L, max = 1_000_000L) fakeTimestampNs: Long,
        @BoolForgery fakeAdmitted: Boolean
    ) {
        // Given
        val mockTimeBank = object : TimeBank {
            override fun consume(executionTime: Long) = Unit
            override fun updateAndCheck(timestamp: Long): Boolean = fakeAdmitted
            override fun timeUntilAvailableInNs(): Long = 0L
        }
        val testedTimeBudget = TimeBankCaptureTimeBudget(mockTimeBank)

        // When
        val admitted = testedTimeBudget.canStart(fakeTimestampNs)

        // Then
        assertThat(admitted).isEqualTo(fakeAdmitted)
    }

    @Test
    fun `M notify skipped frame W requestCapture() { time budget denies admission }`(forge: Forge) {
        // Given
        val fakeTimeBudget = FakeTimeBudget(canStart = false)
        var fakeSkippedFrames = 0
        val fakeFixture = Fixture(forge, timeBudget = fakeTimeBudget, onFrameSkipped = { fakeSkippedFrames++ })
        fakeFixture.testedOrchestrator.start()

        // When
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fakeSkippedFrames).isEqualTo(1)
    }

    @Test
    fun `M notify skipped frame only once W runNext() { same denied request retries without a new draw signal }`(
        forge: Forge
    ) {
        // Given
        val fakeTimeBudget = FakeTimeBudget(canStart = false)
        var fakeSkippedFrames = 0
        val fakeFixture = Fixture(forge, timeBudget = fakeTimeBudget, onFrameSkipped = { fakeSkippedFrames++ })
        fakeFixture.testedOrchestrator.start()
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // When
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fakeSkippedFrames).isEqualTo(1)
    }

    @Test
    fun `M notify each denied draw W requestCapture() { retry pending }`(
        forge: Forge
    ) {
        // Given
        val fakeTimeBudget = FakeTimeBudget(canStart = false)
        var fakeSkippedFrames = 0
        val fakeFixture = Fixture(forge, timeBudget = fakeTimeBudget, onFrameSkipped = { fakeSkippedFrames++ })
        fakeFixture.testedOrchestrator.start()
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // When
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fakeSkippedFrames).isEqualTo(2)
    }

    @Test
    fun `M notify skipped frame again W requestCapture() { a fresh request follows a resolved denial }`(forge: Forge) {
        // Given
        val fakeTimeBudget = FakeTimeBudget(canStart = false)
        var fakeSkippedFrames = 0
        val fakeFixture = Fixture(forge, timeBudget = fakeTimeBudget, onFrameSkipped = { fakeSkippedFrames++ })
        fakeFixture.testedOrchestrator.start()
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        fakeTimeBudget.canStart = true
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)
        fakeFixture.fakeProcessor.pending.single().complete()

        // When
        fakeTimeBudget.canStart = false
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fakeSkippedFrames).isEqualTo(2)
    }

    @Test
    fun `M charge active main thread work W requestCapture() { synchronous capture }`(
        @LongForgery(min = 1L, max = 1_000L) fakeProducerExecutionNs: Long,
        forge: Forge
    ) {
        // Given
        val fakeTimeBudget = FakeTimeBudget(canStart = true)
        val fakeFixture = Fixture(
            forge,
            timeBudget = fakeTimeBudget,
            producerExecutionNs = fakeProducerExecutionNs
        )

        // When
        fakeFixture.testedOrchestrator.start()
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fakeTimeBudget.consumedDurations).containsExactly(fakeProducerExecutionNs)
    }

    @Test
    fun `M exclude asynchronous waiting W complete() { charge recording time bank }`(
        @LongForgery(min = 1L, max = 1_000L) fakeProducerExecutionNs: Long,
        @LongForgery(min = 1L, max = 1_000L) fakeAsynchronousWaitNs: Long,
        forge: Forge
    ) {
        // Given
        val fakeTimeBudget = FakeTimeBudget(canStart = true)
        val fakeFixture = Fixture(
            forge,
            timeBudget = fakeTimeBudget,
            producerExecutionNs = fakeProducerExecutionNs
        )
        fakeFixture.testedOrchestrator.start()
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // When
        fakeFixture.fakeClock.nowNs += fakeAsynchronousWaitNs
        fakeFixture.fakeProcessor.pending.single().complete()

        // Then
        assertThat(fakeTimeBudget.consumedDurations).containsExactly(fakeProducerExecutionNs)
    }

    @Test
    fun `M observe cancellation between synchronous capture units W expire() { generation expires }`(forge: Forge) {
        // Given
        val testedGeneration = forge.aGenerationContext()
        var fakeExecutedUnits = 0

        // When
        val first = testedGeneration.runMainThreadCaptureUnit { fakeExecutedUnits++ }
        testedGeneration.expire()
        val second = testedGeneration.runMainThreadCaptureUnit { fakeExecutedUnits++ }

        // Then
        assertThat(first).isInstanceOf(MainThreadCaptureResult.Completed::class.java)
        assertThat(second).isEqualTo(MainThreadCaptureResult.Interrupted)
        assertThat(fakeExecutedUnits).isEqualTo(1)
    }

    @Test
    fun `M stop further synchronous work W runMainThreadCaptureUnit() { time bank denies continuation }`(forge: Forge) {
        // Given
        val fakeTimeBudget = FakeTimeBudget(canStart = true)
        val testedGeneration = forge.aGenerationContext(mainThreadTimeBudget = fakeTimeBudget)
        var fakeExecutedUnits = 0
        testedGeneration.runMainThreadCaptureUnit(admissionAlreadyGranted = true) { fakeExecutedUnits++ }
        fakeTimeBudget.canStart = false

        // When
        val result = testedGeneration.runMainThreadCaptureUnit { fakeExecutedUnits++ }

        // Then
        assertThat(result).isEqualTo(MainThreadCaptureResult.Interrupted)
        assertThat(fakeExecutedUnits).isEqualTo(1)
    }

    @Test
    fun `M check deadline before synchronous adapter W runMainThreadCaptureUnit() { deadline already reached }`(
        forge: Forge
    ) {
        // Given
        val fakeClock = FakeClock()
        val testedGeneration = forge.aGenerationContext(clock = fakeClock)
        fakeClock.nowNs = testedGeneration.deadlineNs
        var fakeAdapterCalled = false

        // When
        val result = testedGeneration.runMainThreadCaptureUnit { fakeAdapterCalled = true }

        // Then
        assertThat(result).isEqualTo(MainThreadCaptureResult.Interrupted)
        assertThat(fakeAdapterCalled).isFalse()
    }

    @Test
    fun `M check deadline after synchronous adapter W runMainThreadCaptureUnit() { deadline reached during call }`(
        forge: Forge
    ) {
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
    fun `M cooperatively drop capture W requestCapture() { blocking adapter outlives deadline }`(forge: Forge) {
        // Given
        val fakeGenerationBudgetNs = forge.aGenerationBudgetNs()
        val fakeFixture = Fixture(
            forge,
            fakeGenerationBudgetNs = fakeGenerationBudgetNs,
            producerExecutionNs = fakeGenerationBudgetNs
        )

        // When
        fakeFixture.testedOrchestrator.start()
        fakeFixture.testedOrchestrator.requestCapture()
        fakeFixture.fakeCaptureScheduler.runNext(IMMEDIATE)

        // Then
        assertThat(fakeFixture.fakeExpiryScheduler.tasks.single().executed).isFalse()
        assertThat(fakeFixture.fakeProcessor.pending).isEmpty()
        assertThat(fakeFixture.consumedCaptures).isEmpty()
    }

    private class Fixture(
        forge: Forge,
        snapshotToProduce: CapturedFullSnapshot? = forge.aCompositionTestTree().snapshot,
        timeBudget: CaptureTimeBudget = CaptureTimeBudget.UNLIMITED,
        mainThreadExecutor: FakeMainThreadExecutor = FakeMainThreadExecutor(),
        producerExecutionNs: Long = 0L,
        val fakeStartNs: Long = forge.aLong(min = 0L, max = 1_000_000L),
        val fakeGenerationBudgetNs: Long = forge.aGenerationBudgetNs(),
        onProducerCapture: () -> Unit = {},
        onFrameSkipped: () -> Unit = {}
    ) {
        val fakeSnapshot = snapshotToProduce
        val fakeClock = FakeClock().apply { nowNs = fakeStartNs }
        val fakeCaptureScheduler = FakeScheduler()
        val fakeExpiryScheduler = FakeScheduler()
        val fakeProcessor = FakeProcessor()
        val consumedCaptures = mutableListOf<CompletedSnapshotCapture>()
        val producerGenerations = mutableListOf<CaptureGenerationContext>()
        var producerCaptures = 0
        val testedOrchestrator = SnapshotCaptureOrchestrator(
            producer = CapturedSnapshotProducer { generation ->
                producerCaptures++
                producerGenerations += generation
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
            generationBudgetNs = fakeGenerationBudgetNs,
            onFrameSkipped = onFrameSkipped
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
                SnapshotProcessingResult.Completed(generationId, request.snapshot as CapturedFullSnapshot)
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
