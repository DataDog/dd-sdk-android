/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import androidx.annotation.MainThread
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * The single deadline and cancellation scope shared by every phase of a snapshot generation.
 * The deadline is absolute and monotonic; querying it never extends the generation lifetime.
 */
internal class CaptureGenerationContext(
    val id: Long,
    val startedAtNs: Long,
    val deadlineNs: Long,
    private val timeProvider: CaptureTimeProvider,
    private val mainThreadTimeBudget: CaptureTimeBudget = CaptureTimeBudget.UNLIMITED,
    private val sliceBudgetNs: Long = DEFAULT_SLICE_BUDGET_NS,
    private val workRegistry: GenerationWorkRegistry = GenerationWorkRegistry()
) {
    private val state = AtomicReference(State.ACTIVE)
    private val mainThreadWorkAllowed = AtomicBoolean(true)

    /** Cooperative yield checkpoint for one bounded main-thread slice - see [SliceYieldClock]. */
    val sliceClock = SliceYieldClock(startedAtNs, timeProvider, sliceBudgetNs)

    fun remainingBudgetNs(): Long {
        val remaining = deadlineNs - timeProvider.elapsedRealtimeNanos()
        if (remaining <= 0L) {
            expire()
            return 0L
        }
        return remaining
    }

    fun isActive(): Boolean {
        if (timeProvider.elapsedRealtimeNanos() >= deadlineNs) expire()
        return state.get() == State.ACTIVE
    }

    /**
     * Cheap cooperative checkpoint for View/Compose walkers between bounded operations.
     *
     * Reads [mainThreadWorkAllowed] and [isActive] separately rather than as one atomic snapshot -
     * safe because both are one-way latches that only ever move toward "stop" ([state] never
     * returns to [State.ACTIVE] once left; [mainThreadWorkAllowed] never returns to `true`). The
     * gap between the two reads can therefore only make this stale in the safe direction (briefly
     * still `true` after something else already flipped to `false`), never the dangerous one, and
     * the next call - checked before every node - catches it immediately.
     */
    fun shouldContinue(): Boolean = mainThreadWorkAllowed.get() && isActive()

    /**
     * Runs one bounded synchronous capture unit. The caller must invoke this on the main thread.
     * Deadline/cancellation are checked on both sides of the adapter call, and only the time spent
     * actively executing [block] is charged to the recording time bank.
     */
    @MainThread
    fun <T> runMainThreadCaptureUnit(
        admissionAlreadyGranted: Boolean = false,
        block: () -> T
    ): MainThreadCaptureResult<T> {
        val startedAtNs = timeProvider.elapsedRealtimeNanos()
        val wasActive = shouldContinue()
        val admitted = wasActive &&
            (admissionAlreadyGranted || mainThreadTimeBudget.canStart(startedAtNs))
        if (!admitted) {
            if (wasActive) mainThreadWorkAllowed.set(false)
            return MainThreadCaptureResult.Interrupted
        }
        val value = try {
            block()
        } finally {
            mainThreadTimeBudget.consume(timeProvider.elapsedRealtimeNanos() - startedAtNs)
        }
        return if (shouldContinue()) {
            MainThreadCaptureResult.Completed(value)
        } else {
            MainThreadCaptureResult.Interrupted
        }
    }

    fun createWorkToken(): CaptureWorkToken? {
        if (!isActive()) return null
        val token = workRegistry.createToken(this)
        // Same race as track() below: a concurrent expire()/tryAccept() can run its own
        // invalidateAll() sweep between the isActive() check above and the token actually landing
        // in the registry, missing it entirely - it was inserted too late to be caught. Re-checking
        // here and cleaning up explicitly means a caller only ever receives null instead of a
        // token that looks valid for a generation that's already gone.
        return token.takeIf { isActive() } ?: run {
            token.invalidate()
            workRegistry.release(token)
            null
        }
    }

    internal fun track(work: CancellableCaptureWork) {
        if (!isActive()) {
            work.cancel()
            return
        }
        workRegistry.track(work)
        // A concurrent expire()/tryAccept() may run its own invalidateAll() between the isActive()
        // check above and this registration, missing `work` entirely. Cancel unconditionally here
        // rather than only when release() reports it was still present: every CancellableCaptureWork
        // in this codebase tolerates a redundant cancel, so this is safe even if invalidateAll()
        // already cancelled the same work.
        if (!isActive()) {
            workRegistry.release(work)
            work.cancel()
        }
    }

    internal fun expire(): Boolean {
        if (!state.compareAndSet(State.ACTIVE, State.EXPIRED)) return false
        workRegistry.invalidateAll()
        return true
    }

    /** Atomically marks this generation accepted, but only strictly before its deadline. */
    internal fun tryAccept(): Boolean {
        val isBeforeDeadline = timeProvider.elapsedRealtimeNanos() < deadlineNs
        if (!isBeforeDeadline) expire()
        val accepted = isBeforeDeadline && state.compareAndSet(State.ACTIVE, State.ACCEPTED)
        if (accepted) workRegistry.invalidateAll()
        return accepted
    }

    internal fun release(work: CancellableCaptureWork) {
        workRegistry.release(work)
    }

    private enum class State { ACTIVE, EXPIRED, ACCEPTED }

    companion object {
        /** ~120Hz frame budget - small enough to leave most of even that frame for the app itself. */
        val DEFAULT_SLICE_BUDGET_NS: Long = TimeUnit.MILLISECONDS.toNanos(8)
    }
}

internal sealed interface MainThreadCaptureResult<out T> {
    data class Completed<T>(val value: T) : MainThreadCaptureResult<T>
    object Interrupted : MainThreadCaptureResult<Nothing>
}

/**
 * Cooperative checkpoint distinct from [CaptureGenerationContext.shouldContinue]: once [shouldYield]
 * is true, the current slice has been running long enough that a walker should pause and hand back
 * a resumable continuation instead of continuing deeper, so the main thread gets a chance to draw a
 * frame in between. Unlike [CaptureGenerationContext.shouldContinue], a `true` result here is never
 * a reason to discard progress - only to yield it. [markStart] is called by the orchestrator before
 * running or resuming a capture step - never by the walker itself, which only ever reads [shouldYield].
 */
internal class SliceYieldClock(
    startedAtNs: Long,
    private val timeProvider: CaptureTimeProvider,
    private val sliceBudgetNs: Long
) {
    private val sliceStartedAtNs = AtomicLong(startedAtNs)

    fun markStart() {
        sliceStartedAtNs.set(timeProvider.elapsedRealtimeNanos())
    }

    fun shouldYield(): Boolean = timeProvider.elapsedRealtimeNanos() - sliceStartedAtNs.get() >= sliceBudgetNs
}
