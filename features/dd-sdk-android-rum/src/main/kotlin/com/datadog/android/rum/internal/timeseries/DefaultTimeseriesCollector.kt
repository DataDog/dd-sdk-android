/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.scope.RumViewType
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class DefaultTimeseriesCollector(
    private val internalLogger: InternalLogger,
    private val pipelinesFactory: PipelineFactory,
    private val scheduledExecutorService: ScheduledExecutorService,
    initialForeground: Boolean = false
) : TimeseriesCollector {

    private val state = State(initialForeground)
    internal var pipelines: List<Pipeline<*>> = emptyList()
        private set

    // onRumContextUpdate fires on every RUM event, so tryStartCollection must be safe to call
    // repeatedly for the same generation: this remembers the last generation collection was
    // started for, so scheduling only ever happens once per generation.
    private val startedGeneration = AtomicInteger(NO_GENERATION)

    @WorkerThread
    override fun onRumContextUpdate(newRumContext: RumContext) =
        state.setRumContextNonBlocking(newRumContext) { generation, _ -> tryStartCollection(generation) }

    @WorkerThread
    override fun onSessionStart(sessionType: RumSessionType) {
        pipelines = pipelinesFactory.create(sessionType)
        state.setSessionActive(true) { newGeneration, _ -> tryStartCollection(newGeneration) }
    }

    override fun onResumed() =
        state.setForeground(true) { newGeneration, _ -> tryStartCollection(newGeneration) }

    @WorkerThread
    override fun onSessionStop() =
        state.setSessionActive(false) { _, rumContext -> flushPipelines(rumContext) }

    override fun onPaused() = state.setForeground(false) { newGeneration, rumContext ->
        scheduleFlushPipelines(newGeneration, rumContext)
    }

    private fun tryStartCollection(generation: Int) = state.runIfCollecting(generation) {
        if (startedGeneration.getAndSet(generation) == generation) return@runIfCollecting
        pipelines.forEach { pipeline -> scheduledExecutorService.scheduleCollectionIteration(pipeline, generation) }
    }

    private fun ScheduledExecutorService.scheduleCollectionIteration(
        pipeline: Pipeline<*>,
        generation: Int
    ) {
        scheduleSafe(
            TIMESERIES_OPERATION_NAME,
            pipeline.intervalMs,
            TimeUnit.MILLISECONDS,
            internalLogger
        ) {
            try {
                if (!state.runIfCollecting(generation, pipeline::execute)) return@scheduleSafe
            } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
                internalLogger.log(
                    level = InternalLogger.Level.ERROR,
                    targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                    messageBuilder = { ERROR_SAMPLING_FAILED },
                    throwable = t
                )
            } finally {
                state.runIfCollecting(generation) { scheduleCollectionIteration(pipeline, generation) }
            }
        }
    }

    private fun scheduleFlushPipelines(
        stopRequestGeneration: Int,
        rumContextSnapshot: RumContext
    ) = scheduledExecutorService.scheduleSafe(
        SUSPEND_OPERATION_NAME,
        SUSPEND_DELAY_MS,
        TimeUnit.MILLISECONDS,
        internalLogger
    ) {
        if (state.isCollectionProhibited(stopRequestGeneration)) flushPipelines(rumContextSnapshot)
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

    internal companion object {
        const val TIMESERIES_OPERATION_NAME = "Timeseries sampling"
        const val SUSPEND_OPERATION_NAME = "Timeseries suspend"
        const val ERROR_SAMPLING_FAILED = "Timeseries sampling iteration failed; rescheduling next sample."
        const val ERROR_FLUSH_FAILED = "Timeseries flush failed."

        // Generation 0 is a valid generation, so the "not started yet" sentinel must be outside it.
        private const val NO_GENERATION = -1

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

        private class State(private var foreground: Boolean) {

            // Mutated only under the monitor below, but read from the hot onRumContextUpdate path
            // without taking it: volatile already gives visibility, an int can't tear.
            @Volatile
            private var currentGeneration: Int = 0

            // Updated on every RUM event, so it stays outside the monitor too.
            @Volatile
            private var foregroundRumContext: RumContext? = null
            private var sessionActive: Boolean = false

            fun isCollectionProhibited(generation: Int): Boolean = synchronized(this) {
                generation == currentGeneration && !isCollectionAllowed()
            }

            fun runIfCollecting(
                generation: Int,
                block: (RumContext) -> Unit
            ): Boolean = synchronized(this) {
                if (generation == currentGeneration && isCollectionAllowed()) {
                    foregroundRumContext?.let(block)
                    true
                } else {
                    false
                }
            }

            fun setSessionActive(
                isSessionActive: Boolean,
                block: (Int, RumContext) -> Unit
            ) {
                val generation = synchronized(this) {
                    if (isSessionActive == sessionActive) {
                        null
                    } else {
                        sessionActive = isSessionActive
                        ++currentGeneration
                    }
                }
                if (generation != null) foregroundRumContext?.let { block(generation, it) }
            }

            fun setForeground(
                isForeground: Boolean,
                block: (Int, RumContext) -> Unit
            ) {
                val generation = synchronized(this) {
                    if (isForeground == foreground) {
                        null
                    } else {
                        foreground = isForeground
                        ++currentGeneration
                    }
                }
                if (generation != null) foregroundRumContext?.let { block(generation, it) }
            }

            fun setRumContextNonBlocking(
                rumContext: RumContext,
                block: (Int, RumContext) -> Unit
            ) {
                if (foregroundRumContext != rumContext && rumContext.viewType.isForeground) {
                    foregroundRumContext = rumContext
                }

                foregroundRumContext?.let { block(currentGeneration, it) }
            }

            private fun isCollectionAllowed(): Boolean {
                return foreground && sessionActive && foregroundRumContext != null
            }
        }
    }
}
