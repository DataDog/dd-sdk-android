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
 * `foreground` is updated from the main thread, while `sessionActive` and `rumContext` are updated from a worker
 * thread. Sampling callbacks read these properties from the scheduled executor, and `stateGeneration` is accessed
 * from all three threads. The properties are marked with `@Volatile` so that lock-free reads observe updates made
 * by other threads. Compound updates to `foreground`, `sessionActive`, and `stateGeneration` are synchronized.
 *
 * Each change to `sessionActive` or `foreground` advances the state generation.
 * [Looper] uses that generation to reject callbacks scheduled for an obsolete state without explicitly cancelling them.
 *
 * Updates to `rumContext` **do not** advance the generation. This allows an active sampling loop to use the latest
 * context without being restarted, because the context is only needed when creating timeseries events.
 *
 * [setSessionActive] and [setForeground] invoke `onUpdated` when their update opens or closes the gate. They mutate
 * state inside a critical section but invoke the callback after leaving it to avoid accidental deadlocks.
 * [setRumContext] is deliberately not synchronized because it can be called for every RUM event. Supplying a context
 * can open the gate and trigger `onUpdated` without advancing the generation.
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

    fun setForeground(
        isForeground: Boolean,
        onUpdated: (Int, RumContext) -> Unit = { _, _ -> }
    ) {
        var contextToFlush: RumContext? = null
        val generation = synchronized(this) {
            if (isForeground == this@Gate.isForeground) {
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
