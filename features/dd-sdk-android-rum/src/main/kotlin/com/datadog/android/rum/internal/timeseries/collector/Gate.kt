/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.collector

import com.datadog.android.rum.internal.domain.RumContext

/**
 * Combines the states that control whether timeseries collection is allowed.
 *
 * `foreground` can be updated concurrently from any thread (a debounced background transition can be pending
 * on a scheduled executor while a new resume arrives on the main thread), while `sessionActive` and `rumContext`
 * are updated from a worker thread. Sampling callbacks read these properties from the scheduled executor, and
 * `stateGeneration` is accessed from all three threads. The properties are marked with `@Volatile` so that
 * lock-free reads observe updates made by other threads. Compound updates to `foreground`, `sessionActive`,
 * and `stateGeneration` are synchronized.
 *
 * Each change to `sessionActive` or `foreground` advances the state generation.
 * [Looper] uses that generation to reject callbacks scheduled for an obsolete state without explicitly cancelling them.
 *
 * A caller that wants to debounce a `foreground` update (e.g. delay reporting the app as backgrounded) claims a
 * ticket with [startBackgroundTransition] before scheduling the delayed work, then applies it with
 * [setForeground]. Because the ticket check and the state mutation happen inside the same critical
 * section, a later transition (whether applied immediately or through another debounced call) atomically
 * supersedes the pending one, regardless of which thread or how many threads apply these updates.
 *
 * Updates to `rumContext` **do not** advance the generation. This allows an active sampling loop to use the latest
 * context without being restarted, because the context is only needed when creating timeseries events.
 *
 * [setSessionActive] and [setForeground] invoke `onUpdated` when their update opens or closes the gate.
 * They mutate state inside a critical section but invoke the callback after leaving it to avoid accidental
 * deadlocks. [setRumContext] is deliberately not synchronized because it can be called for every RUM event.
 * Supplying a context can open the gate and trigger `onUpdated` without advancing the generation.
 */
internal class Gate {
    @Volatile
    private var stateGeneration: Int = 0

    @Volatile
    private var isForeground: Boolean = false

    @Volatile
    private var rumContext: RumContext? = null

    @Volatile
    private var isSessionActive: Boolean = false

    private var foregroundTransitionId: Int = 0

    fun runIfGateOpen(
        generation: Int,
        runnable: (RumContext) -> Unit
    ): Boolean {
        val rumContext = synchronized(this) {
            if (isGateOpened(generation)) rumContext else null
        }

        return if (rumContext != null) {
            runnable.invoke(rumContext)
            true
        } else {
            false
        }
    }

    fun setSessionActive(
        isActive: Boolean,
        onUpdated: (Int, RumContext) -> Unit
    ) {
        var contextToFlush: RumContext? = null
        val generation = synchronized(this) {
            if (isActive == isSessionActive) {
                null
            } else {
                val wasOpened = isGateOpened()
                contextToFlush = rumContext
                isSessionActive = isActive
                if (!isActive) rumContext = null
                ++stateGeneration
                if (wasOpened == isGateOpened()) null else stateGeneration
            }
        }
        if (generation != null && contextToFlush != null) onUpdated(generation, contextToFlush)
    }

    /**
     * Claims a ticket for an intended `foreground` transition. Present it to [setForeground] to apply
     * it only if no later transition (immediate or debounced) has superseded it in the meantime.
     */
    fun startBackgroundTransition(): Int = synchronized(this) {
        ++foregroundTransitionId
    }

    /**
     * Applies [isForeground] only if [transitionId] is still the most recently claimed one. The check and the mutation
     * happen inside the same critical section, so this is safe to call concurrently from multiple threads.
     */
    fun setForeground(
        isForeground: Boolean,
        transitionId: Int = startBackgroundTransition(),
        onUpdated: (Int, RumContext) -> Unit = { _, _ -> }
    ) {
        var contextToFlush: RumContext? = null
        val generation = synchronized(this) {
            if (transitionId != foregroundTransitionId || isForeground == this@Gate.isForeground) {
                null
            } else {
                val wasOpened = isGateOpened()
                contextToFlush = rumContext
                this@Gate.isForeground = isForeground
                ++stateGeneration
                if (wasOpened == isGateOpened()) null else stateGeneration
            }
        }
        if (generation != null && contextToFlush != null) onUpdated(generation, contextToFlush)
    }

    fun setRumContext(
        rumContext: RumContext,
        onUpdated: (Int, RumContext) -> Unit
    ) {
        if (this.rumContext == rumContext) return
        val wasOpened = isGateOpened()
        this.rumContext = rumContext
        if (wasOpened != isGateOpened()) onUpdated(stateGeneration, rumContext)
    }

    fun getRumContext(): RumContext? = synchronized(this) {
        rumContext?.takeIf { isGateOpened() }
    }

    private fun isGateOpened(generation: Int): Boolean = generation == stateGeneration && isGateOpened()

    private fun isGateOpened(): Boolean = isForeground && isSessionActive && rumContext != null
}
