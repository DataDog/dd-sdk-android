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
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Per-session timeseries collector.
 *
 * Lifecycle:
 *  - Each instance is single-use: exactly one [onSessionStart] followed by exactly one [onSessionStop].
 *    Duplicate calls are no-ops, and `RumSessionScope` builds a fresh instance per session.
 *  - [onViewTypeUpdate] is only meaningful between those two calls.
 *
 * Foreground gating:
 *  - Sampling only runs while the app is in the foreground, so [onSessionStart] starts a sampling
 *    chain only when the session begins on a foreground view.
 *  - Leaving the foreground schedules a deferred suspension which flushes whatever is buffered, so
 *    the pending batch reaches the writer instead of being dropped when the app is backgrounded.
 *  - Returning to the foreground cancels a suspension that has not fired yet, or starts a fresh
 *    sampling generation if it already did.
 *
 * Threading:
 *  - [onSessionStart] / [onSessionStop] / [onViewTypeUpdate] are called from the RUM event-handler thread.
 *  - Sampling and suspension tasks run on a [scheduledExecutorService] shared with other RUM
 *    components (owned by the SDK core); the instance neither owns nor shuts it down.
 *  - Access to a [Pipeline] is serialized on the pipeline instance, so a flush from the suspension
 *    task cannot interleave with a sampling tick.
 *  - Duplicate-chain prevention: each sampling chain carries a generation number. When a new
 *    generation starts, in-flight or queued ticks from the previous one self-terminate on their
 *    first check.
 */
internal class DefaultTimeseriesCollector(
    private val internalLogger: InternalLogger,
    internal val pipelines: List<Pipeline<*>>,
    internal val scheduledExecutorService: ScheduledExecutorService,
    @Volatile private var currentViewType: RumViewType?
) : TimeseriesCollector {

    private val state = State()

    @Volatile
    private var recentSuspension: ScheduledFuture<*>? = null

    // region PUBLIC API

    @WorkerThread
    override fun onSessionStart() {
        state.set(isActive = currentViewType.isForeground)?.let { generation -> scheduleSampling(generation) }
    }

    @WorkerThread
    override fun onSessionStop() {
        cancelSuspension()
        if (state.set(false) != null) {
            flushPipelines()
        }
    }

    @WorkerThread
    override fun onViewTypeUpdate(newViewType: RumViewType) {
        val oldViewType = currentViewType
        if (newViewType == oldViewType) return

        val isEnterForeground = !oldViewType.isForeground && newViewType.isForeground
        val isLeaveForeground = oldViewType.isForeground && !newViewType.isForeground

        currentViewType = newViewType

        if (isLeaveForeground) {
            scheduleStop(state.currentGeneration)
        } else if (isEnterForeground) {
            scheduleSampling(generation = state.startGeneration())
        }
    }

    // endregion

    // region SAMPLING

    private fun scheduleSampling(generation: Int) {
        cancelSuspension()
        pipelines.forEach { pipeline -> scheduledExecutorService.schedulePipeline(pipeline, generation) }
    }

    private fun ScheduledExecutorService.schedulePipeline(
        pipeline: Pipeline<*>,
        generation: Int
    ) {
        scheduleSafe(
            TIMESERIES_OPERATION_NAME,
            pipeline.intervalMs,
            TimeUnit.MILLISECONDS,
            internalLogger
        ) {
            if (!state.isGenerationActive(generation)) return@scheduleSafe
            try {
                // The chain stays alive during the suspension delay, so a tick landing while the
                // app is already out of the foreground must skip the sample but keep the chain.
                if (currentViewType.isForeground) {
                    synchronized(pipeline) { pipeline.execute() }
                }
            } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
                internalLogger.log(
                    level = InternalLogger.Level.ERROR,
                    targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                    messageBuilder = { ERROR_SAMPLING_FAILED },
                    throwable = t
                )
            } finally {
                if (state.isGenerationActive(generation)) schedulePipeline(pipeline, generation)
            }
        }
    }

    @WorkerThread
    private fun flushPipelines() {
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

    // endregion

    // region SUSPENSION

    private fun scheduleStop(stopRequestGeneration: Int) {
        recentSuspension = scheduledExecutorService.scheduleSafe(
            SUSPEND_OPERATION_NAME,
            SUSPEND_DELAY_MS,
            TimeUnit.MILLISECONDS,
            internalLogger
        ) {
            if (!currentViewType.isForeground && state.stopGeneration(stopRequestGeneration)) {
                flushPipelines()
            }
        }
    }

    private fun cancelSuspension() {
        recentSuspension?.cancel(false)
        recentSuspension = null
    }

    // endregion

    internal companion object {
        const val TIMESERIES_OPERATION_NAME = "Timeseries sampling"
        const val SUSPEND_OPERATION_NAME = "Timeseries suspend"
        const val ERROR_SAMPLING_FAILED = "Timeseries sampling iteration failed; rescheduling next sample."
        const val ERROR_FLUSH_FAILED = "Timeseries flush failed."

        // Matches ActivityViewTrackingStrategy.STOP_VIEW_DELAY_MS, which guards the same race:
        // an Activity-to-Activity transition leaves no active view for a moment when the tracking
        // strategy stops the view on pause rather than on stop.
        const val SUSPEND_DELAY_MS = 200L

        val RumViewType?.isForeground: Boolean
            get() = when (this) {
                RumViewType.FOREGROUND, RumViewType.APPLICATION_LAUNCH -> true
                RumViewType.BACKGROUND -> false
                RumViewType.NONE -> false
                null -> false
            }

        private class State {
            // Written under the monitor only, but read outside of it, hence @Volatile: a stale
            // generation would make the guards below reject a live sampling chain or flush.
            @Volatile
            var currentGeneration: Int = 0
                private set

            private var active: Boolean = false

            fun isGenerationActive(generation: Int): Boolean = synchronized(this) {
                currentGeneration == generation && active
            }

            /**
             * Always starts a fresh generation, so that a suspension pending on the previous one
             * can no longer stop the sampling chain. Returns the new generation.
             */
            fun startGeneration(): Int = synchronized(this) {
                active = true
                ++currentGeneration
            }

            /**
             * Deactivates [generation] if it is still the current one.
             * Returns true when this call is the one that deactivated it.
             */
            fun stopGeneration(generation: Int): Boolean = synchronized(this) {
                currentGeneration == generation && set(false) != null
            }

            /** Returns the new generation if this call changed the state, null otherwise. */
            fun set(isActive: Boolean): Int? = synchronized(this) {
                if (isActive == active) {
                    null
                } else {
                    active = isActive
                    ++currentGeneration
                }
            }
        }
    }
}
