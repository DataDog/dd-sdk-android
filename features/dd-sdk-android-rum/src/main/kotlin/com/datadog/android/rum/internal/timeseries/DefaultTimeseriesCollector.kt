/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.rum.internal.domain.scope.RumViewType
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Per-session timeseries collector.
 *
 * Lifecycle invariants (enforced by [onSessionStart]/[onSessionStop] idempotency):
 *  - Each instance is single-use: exactly one [onSessionStart] followed by exactly one [onSessionStop].
 *  - Multiple [onSessionStart] / [onSessionStop] calls are safe — duplicate calls are no-ops.
 *  - After [onSessionStop], the instance must not be restarted; create a new instance.
 *
 * Background sampling:
 *  - When [collectInBackground] is `false`, [onViewTypeUpdate] pauses sampling on leaving foreground
 *    and resumes it on returning to foreground.
 *
 * Threading:
 *  - [onSessionStart] / [onSessionStop] / [onViewTypeUpdate] are called from the RUM event-handler thread.
 *  - Sampling tasks run on a [scheduledExecutorService] shared with other RUM components
 *    (owned by the SDK core); the instance neither owns nor shuts it down.
 *  - [Pipeline] is responsible for its own thread safety via internal synchronization.
 *  - Duplicate-chain prevention: each sampling chain carries a generation number.
 *    When [startSampling] starts a new generation, any in-flight or queued ticks from the
 *    previous generation self-terminate on their first check.
 */
internal class DefaultTimeseriesCollector(
    private val internalLogger: InternalLogger,
    internal val pipelines: List<Pipeline<*>>,
    private val collectInBackground: Boolean,
    internal val scheduledExecutorService: ScheduledExecutorService
) : TimeseriesCollector {

    private enum class State { IDLE, RUNNING, SUSPENDED, STOPPED }
    private val state = AtomicReference(State.IDLE)

    // Incremented on every start/resume. Ticks carrying a stale generation self-terminate.
    private val currentGeneration = AtomicInteger(0)

    @Volatile
    private var currentViewType: RumViewType? = null

    @WorkerThread
    override fun onSessionStart() {
        if (state.compareAndSet(State.IDLE, State.RUNNING)) {
            startSampling()
        }
    }

    @WorkerThread
    override fun onSessionStop() {
        if (state.getAndSet(State.STOPPED) != State.STOPPED) {
            pipelines.forEach { pipeline ->
                try {
                    synchronized(pipeline, pipeline::flush)
                } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
                    internalLogger.log(
                        level = InternalLogger.Level.ERROR,
                        targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                        messageBuilder = { ERROR_FLUSH_FAILED },
                        throwable = t
                    )
                }
            }
        }
    }

    @WorkerThread
    override fun onViewTypeUpdate(newViewType: RumViewType) {
        if (newViewType == currentViewType) return
        val isEnterForeground = !currentViewType.isForeground && newViewType.isForeground
        currentViewType = newViewType
        if (!collectInBackground) {
            if (isEnterForeground && state.compareAndSet(State.SUSPENDED, State.RUNNING)) {
                startSampling()
            } else if (!newViewType.isForeground) {
                state.compareAndSet(State.RUNNING, State.SUSPENDED)
            }
        }
    }

    private fun startSampling() {
        val generation = currentGeneration.incrementAndGet()
        pipelines.forEach { schedulePipeline(it, generation) }
    }

    private fun schedulePipeline(pipeline: Pipeline<*>, generation: Int) {
        scheduledExecutorService.scheduleSafe(
            OPERATION_NAME,
            pipeline.intervalMs,
            TimeUnit.MILLISECONDS,
            internalLogger
        ) {
            if (!isActive(generation)) return@scheduleSafe
            try {
                synchronized(pipeline) {
                    if (isActive(generation)) pipeline.execute()
                }
            } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
                internalLogger.log(
                    level = InternalLogger.Level.ERROR,
                    targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                    messageBuilder = { ERROR_SAMPLING_FAILED },
                    throwable = t
                )
            } finally {
                if (isActive(generation)) schedulePipeline(pipeline, generation)
            }
        }
    }

    private fun isActive(generation: Int): Boolean =
        state.get() == State.RUNNING && currentGeneration.get() == generation

    internal companion object {
        const val OPERATION_NAME = "Timeseries sampling"
        const val ERROR_SAMPLING_FAILED = "Timeseries sampling iteration failed; rescheduling next sample."
        const val ERROR_FLUSH_FAILED = "Timeseries flush on session stop failed."
        val RumViewType?.isForeground: Boolean
            get() = when (this) {
                RumViewType.FOREGROUND, RumViewType.APPLICATION_LAUNCH -> true
                RumViewType.BACKGROUND -> false
                RumViewType.NONE -> false
                null -> false
            }
    }
}
