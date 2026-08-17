/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

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
    private val internalLogger: InternalLogger = InternalLogger.UNBOUND
) {
    private val lock = Any()
    private var isRunning = false
    private var captureRequested = false
    private var captureScheduled = false
    private var captureScheduleId = 0L
    private var nextGenerationId = 1L
    private var activeGeneration: ActiveGeneration? = null
    private var pendingChangeset: CaptureChangeset = CaptureChangeset.EMPTY

    fun start() {
        synchronized(lock) { isRunning = true }
    }

    fun stop() {
        val workToCancel = synchronized(lock) {
            isRunning = false
            captureRequested = false
            captureScheduled = false
            captureScheduleId++
            pendingChangeset = CaptureChangeset.EMPTY
            activeGeneration?.also { activeGeneration = null }
        }
        workToCancel?.cancel()
    }

    fun shutdown() {
        stop()
        captureScheduler.shutdown()
        if (expiryScheduler !== captureScheduler) expiryScheduler.shutdown()
    }

    fun requestCapture(changeset: CaptureChangeset = CaptureChangeset.EMPTY) {
        val shouldSchedule = synchronized(lock) {
            if (!isRunning) return
            captureRequested = true
            pendingChangeset = pendingChangeset.mergedWith(changeset)
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
            mainThreadExecutor.execute { beginCapture(scheduleId) }
        }
    }

    private fun beginCapture(scheduleId: Long) {
        val active = createActiveGeneration(scheduleId) ?: return

        val expiration = expiryScheduler.schedule(active.generation.remainingBudgetNs()) {
            expire(active.generation)
        }
        val shouldCancelExpiration = synchronized(lock) {
            val current = activeGeneration
            if (current?.generation?.id == active.generation.id) {
                current.expiration = expiration
                active.generation.track(expiration)
                false
            } else {
                true
            }
        }
        if (shouldCancelExpiration) {
            expiration.cancel()
            return
        }

        val captureResult = active.generation.runMainThreadCaptureUnit(admissionAlreadyGranted = true) {
            safeCapture(producer, internalLogger, active.generation, active.changeset)
        }
        val snapshot = when (captureResult) {
            is MainThreadCaptureResult.Completed -> captureResult.value
            MainThreadCaptureResult.Interrupted -> null
        }
        if (snapshot != null && active.generation.isActive()) {
            val processing = processor.process(
                SnapshotProcessingRequest(active.generation, snapshot),
                SnapshotProcessingCallback(::onProcessed)
            )
            val shouldCancel = synchronized(lock) {
                val current = activeGeneration
                if (current?.generation?.id == active.generation.id) {
                    current.processing = processing
                    active.generation.track(processing)
                    false
                } else {
                    true
                }
            }
            if (shouldCancel) processing.cancel()
        } else {
            expire(active.generation)
        }
    }

    private fun createActiveGeneration(scheduleId: Long): ActiveGeneration? = synchronized(lock) {
        if (captureScheduleId != scheduleId) return@synchronized null
        captureScheduled = false
        if (!canScheduleCapture) return@synchronized null

        captureRequested = false
        val eligibilityTimestampNs = timeProvider.elapsedRealtimeNanos()
        if (!timeBudget.canStart(eligibilityTimestampNs)) return@synchronized null
        // Only drain the accumulated changeset once a generation is actually admitted; an
        // earlier denial here must leave it intact so the next successful generation still sees
        // everything that changed since the last one it actually processed.
        val changeset = pendingChangeset
        pendingChangeset = CaptureChangeset.EMPTY
        // Nothing that belongs to capture runs before this timestamp. The producer's first action
        // is window/root discovery, so every capture phase shares the deadline created here.
        val startedAtNs = timeProvider.elapsedRealtimeNanos()
        ActiveGeneration(
            CaptureGenerationContext(
                id = nextGenerationId++,
                startedAtNs = startedAtNs,
                deadlineNs = saturatedAdd(startedAtNs, generationBudgetNs),
                timeProvider = timeProvider,
                mainThreadTimeBudget = timeBudget
            ),
            changeset = changeset
        ).also { activeGeneration = it }
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

        expired?.cancel()
        completed?.takeIf { it.generation.isActive() }?.let(consumer::consume)
        scheduleCaptureIfRequested()
    }

    private fun expire(generation: CaptureGenerationContext) {
        generation.expire()
        val expired = synchronized(lock) {
            val active = activeGeneration
            if (active?.generation !== generation) return
            activeGeneration = null
            active
        }
        expired.cancel()
        scheduleCaptureIfRequested()
    }

    private fun scheduleCaptureIfRequested() {
        val shouldSchedule = synchronized(lock) { canScheduleCapture }
        if (shouldSchedule) scheduleCapture()
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
        val changeset: CaptureChangeset,
        var expiration: CancellableCaptureWork = CancellableCaptureWork.NONE,
        var processing: CancellableCaptureWork = CancellableCaptureWork.NONE
    ) {
        fun cancel() {
            generation.expire()
        }
    }

    private companion object {
        val DEFAULT_CAPTURE_DELAY_NS: Long = TimeUnit.MILLISECONDS.toNanos(64)
        val DEFAULT_GENERATION_BUDGET_NS: Long = TimeUnit.MILLISECONDS.toNanos(90)

        fun saturatedAdd(value: Long, increment: Long): Long =
            if (increment > 0 && value > Long.MAX_VALUE - increment) Long.MAX_VALUE else value + increment
    }
}

/** The producer walks live application/Compose state and can throw for reasons outside our control. */
@Suppress("TooGenericExceptionCaught")
private fun safeCapture(
    producer: CapturedSnapshotProducer,
    internalLogger: InternalLogger,
    generation: CaptureGenerationContext,
    changeset: CaptureChangeset
): CapturedFullSnapshot? = try {
    producer.capture(generation, changeset)
} catch (e: Exception) {
    internalLogger.log(
        InternalLogger.Level.ERROR,
        InternalLogger.Target.TELEMETRY,
        { "Composition snapshot producer threw an exception" },
        e
    )
    null
}
