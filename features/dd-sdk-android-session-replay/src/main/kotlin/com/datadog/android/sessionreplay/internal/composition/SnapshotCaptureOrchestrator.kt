/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import androidx.annotation.MainThread
import com.datadog.android.api.InternalLogger
import java.util.concurrent.TimeUnit

/**
 * Serializes snapshot generations and owns their complete lifetime: traversal, asynchronous
 * processing, expiry, and handoff. A draw signal only calls [requestCapture]; it owns no capture
 * state. At most one generation is active, while additional signals coalesce into one follow-up.
 */
internal class SnapshotCaptureOrchestrator(
    private val producer: CapturedSnapshotProducer,
    private val processor: CapturedSnapshotProcessor,
    private val consumer: CompletedSnapshotConsumer,
    private val timeProvider: CaptureTimeProvider,
    private val captureScheduler: CaptureTaskScheduler,
    private val mainThreadExecutor: CaptureMainThreadExecutor,
    private val expiryScheduler: CaptureTaskScheduler,
    private val timeBudget: CaptureTimeBudget = CaptureTimeBudget.UNLIMITED,
    private val captureDelayNs: Long = DEFAULT_CAPTURE_DELAY_NS,
    private val generationBudgetNs: Long = DEFAULT_GENERATION_BUDGET_NS,
    private val sliceBudgetNs: Long = CaptureGenerationContext.DEFAULT_SLICE_BUDGET_NS,
    private val internalLogger: InternalLogger = InternalLogger.UNBOUND
) {
    private val lock = Any()
    private var isRunning = false
    private var captureRequested = false
    private var captureScheduled = false
    private var captureScheduleId = 0L
    private var nextGenerationId = 1L
    private var activeGeneration: ActiveGeneration? = null

    fun start() {
        synchronized(lock) { isRunning = true }
    }

    fun stop() {
        val workToCancel = synchronized(lock) {
            isRunning = false
            captureRequested = false
            captureScheduled = false
            captureScheduleId++
            activeGeneration?.also { activeGeneration = null }
        }
        workToCancel?.cancel()
    }

    fun shutdown() {
        stop()
        captureScheduler.shutdown()
        if (expiryScheduler !== captureScheduler) expiryScheduler.shutdown()
    }

    fun requestCapture() {
        val shouldSchedule = synchronized(lock) {
            if (!isRunning) return
            captureRequested = true
            activeGeneration == null && !captureScheduled
        }
        if (shouldSchedule) scheduleCapture()
    }

    private fun scheduleCapture() {
        val scheduleId = synchronized(lock) {
            if (!canScheduleCapture) return
            captureScheduled = true
            ++captureScheduleId
        }
        captureScheduler.schedule(captureDelayNs) {
            @Suppress("ThreadSafety") // mainThreadExecutor posts this block onto the main thread.
            mainThreadExecutor.execute { beginCapture(scheduleId) }
        }
    }

    @MainThread
    private fun beginCapture(scheduleId: Long) {
        val active = synchronized(lock) {
            if (captureScheduleId != scheduleId) return@synchronized null
            captureScheduled = false
            if (!canScheduleCapture) return@synchronized null

            val eligibilityTimestampNs = timeProvider.elapsedRealtimeNanos()
            // A denial here must leave captureRequested set - otherwise nothing retries this
            // request once the time bank replenishes, since no ActiveGeneration exists yet to
            // later trigger scheduleCapture() via expire()/onProcessed() - both call it
            // unconditionally and rely on its own canScheduleCapture guard, same as this does when
            // returning null below.
            if (!timeBudget.canStart(eligibilityTimestampNs)) return@synchronized null
            captureRequested = false
            // Nothing that belongs to capture runs before this timestamp. The producer's first
            // action is window/root discovery, so every capture phase shares the deadline created
            // here.
            val startedAtNs = timeProvider.elapsedRealtimeNanos()
            ActiveGeneration(
                CaptureGenerationContext(
                    id = nextGenerationId++,
                    startedAtNs = startedAtNs,
                    deadlineNs = saturatedAdd(startedAtNs, generationBudgetNs),
                    timeProvider = timeProvider,
                    mainThreadTimeBudget = timeBudget,
                    sliceBudgetNs = sliceBudgetNs
                )
            ).also { activeGeneration = it }
        } ?: return scheduleCapture()

        val expiration = expiryScheduler.schedule(active.generation.remainingBudgetNs()) {
            expire(active.generation)
        }
        if (!active.trackOrCancel(lock, { activeGeneration }, expiration) { it.expiration = expiration }) return

        active.generation.sliceClock.markStart()
        runCaptureSlice(active) { producer.capture(active.generation) }
    }

    /**
     * Runs one bounded main-thread slice of [step]. A [CaptureStep.Yielded] result means [step]
     * didn't finish within this slice's budget - the continuation is rescheduled via
     * [mainThreadExecutor] rather than run inline, so the Looper gets a chance to process a frame
     * or input event before it resumes. The overall generation deadline still wins regardless of
     * how many slices this takes: [CaptureGenerationContext.runMainThreadCaptureUnit] checks it
     * before every slice, including a resume, so a generation that outlives its deadline between
     * slices is interrupted without even running the next one, let alone acting on its result.
     */
    @MainThread
    private fun runCaptureSlice(active: ActiveGeneration, step: () -> CaptureStep<CapturedFullSnapshot?>) {
        val captureResult = active.generation.runMainThreadCaptureUnit(admissionAlreadyGranted = true) {
            safeCaptureStep(internalLogger, step)
        }
        when (captureResult) {
            is MainThreadCaptureResult.Completed -> when (val stepResult = captureResult.value) {
                is CaptureStep.Done -> finishCapture(active, stepResult.value)
                is CaptureStep.Yielded -> {
                    active.generation.sliceClock.markStart()
                    val continuation = mainThreadExecutor.execute { runCaptureSlice(active, stepResult.resume) }
                    active.generation.track(continuation)
                }
            }
            MainThreadCaptureResult.Interrupted -> expire(active.generation)
        }
    }

    @MainThread
    private fun finishCapture(active: ActiveGeneration, snapshot: CapturedFullSnapshot?) {
        if (snapshot == null || !active.generation.isActive()) {
            expire(active.generation)
            return
        }
        val processing = processor.process(
            SnapshotProcessingRequest(active.generation, snapshot),
            SnapshotProcessingCallback(::onProcessed)
        )
        active.trackOrCancel(lock, { activeGeneration }, processing) { it.processing = processing }
    }

    private fun onProcessed(result: SnapshotProcessingResult) {
        var expired: ActiveGeneration? = null
        val completed = synchronized(lock) {
            val active = activeGeneration
            if (!isRunning || active?.generation?.id != result.generationId) return
            if (!active.generation.isActive()) {
                activeGeneration = null
                expired = active
                return@synchronized null
            }

            activeGeneration = null
            active.generation.release(active.processing)
            if (result is SnapshotProcessingResult.Completed) {
                CompletedSnapshotCapture(active.generation, result.snapshot)
            } else {
                active.generation.expire()
                null
            }
        }

        // completed is only non-null when isActive() passed above, while still holding `lock` -
        // and expire() (below) now only ever mutates generation state while holding that same
        // lock too, so nothing can flip it out from under us before consume() runs. This used to
        // be a separate isActive() re-check here, running unsynchronized after the lock above was
        // already released - which could race a concurrent expire() firing from the expiry
        // scheduler's own thread: it could see the generation already expired and drop the
        // snapshot silently, while expire() itself found activeGeneration already cleared. Removed
        // rather than duplicated: with expire() closing the gap on its side, this decision is final.
        expired?.cancel()
        completed?.let(consumer::consume)
        scheduleCapture()
    }

    private fun expire(generation: CaptureGenerationContext) {
        val expired = synchronized(lock) {
            val active = activeGeneration
            if (active?.generation !== generation) return
            generation.expire()
            activeGeneration = null
            active
        }
        expired.cancel()
        scheduleCapture()
    }

    private val canScheduleCapture: Boolean
        get() = when {
            !isRunning -> false
            activeGeneration != null -> false
            captureScheduled -> false
            else -> captureRequested
        }

    private class ActiveGeneration(
        val generation: CaptureGenerationContext,
        var expiration: CancellableCaptureWork = CancellableCaptureWork.NONE,
        var processing: CancellableCaptureWork = CancellableCaptureWork.NONE
    ) {
        fun cancel() {
            generation.expire()
        }

        /**
         * Attaches [work] to this generation's cancellation lifecycle, but only while
         * [currentGeneration] still returns this same generation - the identity check and [assign]
         * happen together under [lock] so a concurrent generation swap can't land in between and
         * leave [work] tracked against a generation the orchestrator has already moved on from.
         * Cancels [work] and returns `false` when that's exactly what happened.
         */
        fun trackOrCancel(
            lock: Any,
            currentGeneration: () -> ActiveGeneration?,
            work: CancellableCaptureWork,
            assign: (ActiveGeneration) -> Unit
        ): Boolean {
            val tracked = synchronized(lock) {
                val current = currentGeneration()
                if (current?.generation?.id != generation.id) return@synchronized false
                assign(current)
                generation.track(work)
                true
            }
            if (!tracked) work.cancel()
            return tracked
        }
    }

    private companion object {
        val DEFAULT_CAPTURE_DELAY_NS: Long = TimeUnit.MILLISECONDS.toNanos(64)
        val DEFAULT_GENERATION_BUDGET_NS: Long = TimeUnit.MILLISECONDS.toNanos(90)
    }
}

private fun saturatedAdd(value: Long, increment: Long): Long =
    if (increment > 0 && value > Long.MAX_VALUE - increment) Long.MAX_VALUE else value + increment

/**
 * Deliberately broad rather than targeted: this runs on the main thread, so an escaping throwable
 * crashes the host app. The producer walks live application and Compose state whose failure types
 * cannot be enumerated from here, and a failed traversal must only cost one snapshot. Wraps every
 * slice - the initial call and every [CaptureStep.Yielded] resume alike - since a resume closure
 * re-enters the same producer-owned walking code and can fail for the same reasons.
 */
@Suppress("TooGenericExceptionCaught")
private fun safeCaptureStep(
    internalLogger: InternalLogger,
    step: () -> CaptureStep<CapturedFullSnapshot?>
): CaptureStep<CapturedFullSnapshot?> = try {
    step()
} catch (e: Exception) {
    internalLogger.log(
        InternalLogger.Level.ERROR,
        InternalLogger.Target.TELEMETRY,
        { "Composition snapshot producer threw an exception" },
        e
    )
    CaptureStep.Done(null)
}
