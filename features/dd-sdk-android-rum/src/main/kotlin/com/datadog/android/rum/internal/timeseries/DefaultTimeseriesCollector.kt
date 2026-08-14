/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.scope.RumViewType
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class DefaultTimeseriesCollector(
    private val internalLogger: InternalLogger,
    internal val pipelines: List<Pipeline<*>>,
    internal val scheduledExecutorService: ScheduledExecutorService,
    @Volatile private var rumContext: RumContext
) : TimeseriesCollector {
    private val state = State()

    // Batches are attributed to the view they are sent from, but a flush can happen once the app
    // already left the foreground, where there is no view to attribute to. Keep the last foreground
    // context so those batches stay attached to the view they were sent from.
    @Volatile
    private var lastForegroundRumContext: RumContext = rumContext

    @Volatile
    private var recentSuspension: ScheduledFuture<*>? = null

    // region PUBLIC API
    @WorkerThread
    override fun onSessionStart() {
        state.set(isActive = rumContext.viewType.isForeground)?.let { generation -> scheduleSampling(generation) }
    }

    @WorkerThread
    override fun onSessionStop() {
        cancelSuspension()
        if (state.set(false) != null) {
            flushPipelines(lastForegroundRumContext)
        }
    }

    @WorkerThread
    override fun onRumContextUpdate(newRumContext: RumContext) {
        val oldViewType = rumContext.viewType
        val newViewType = newRumContext.viewType

        val isEnterForeground = !oldViewType.isForeground && newViewType.isForeground
        val isLeaveForeground = oldViewType.isForeground && !newViewType.isForeground

        rumContext = newRumContext
        if (newViewType.isForeground) lastForegroundRumContext = newRumContext

        if (isLeaveForeground) {
            scheduleStop(state.currentGeneration)
        } else if (isEnterForeground) {
            scheduleSampling(generation = state.startGeneration())
        }
    }

    // endregion

    //region SAMPLING

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
                val currentRumContext = rumContext
                if (currentRumContext.viewType.isForeground) {
                    pipeline.execute(currentRumContext)
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
    private fun flushPipelines(rumContext: RumContext) {
        pipelines.forEach { pipeline ->
            try {
                pipeline.flush(rumContext)
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
            if (!rumContext.viewType.isForeground && state.stopGeneration(stopRequestGeneration)) {
                flushPipelines(lastForegroundRumContext)
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
