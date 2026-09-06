/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import java.util.concurrent.atomic.AtomicBoolean
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
    private val workRegistry: GenerationWorkRegistry = GenerationWorkRegistry()
) {
    private val state = AtomicReference(State.ACTIVE)
    private val mainThreadWorkAllowed = AtomicBoolean(true)

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

    /** Cheap cooperative checkpoint for View/Compose walkers between bounded operations. */
    fun shouldContinue(): Boolean = mainThreadWorkAllowed.get() && isActive()

    /**
     * Runs one bounded synchronous capture unit. The caller must invoke this on the main thread.
     * Deadline/cancellation are checked on both sides of the adapter call, and only the time spent
     * actively executing [block] is charged to the recording time bank.
     */
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

    internal fun expire(): Boolean = transitionTo(State.EXPIRED)

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

    private fun transitionTo(newState: State): Boolean {
        if (!state.compareAndSet(State.ACTIVE, newState)) return false
        workRegistry.invalidateAll()
        return true
    }

    private enum class State { ACTIVE, EXPIRED, ACCEPTED }
}

internal sealed interface MainThreadCaptureResult<out T> {
    data class Completed<T>(val value: T) : MainThreadCaptureResult<T>
    object Interrupted : MainThreadCaptureResult<Nothing>
}
