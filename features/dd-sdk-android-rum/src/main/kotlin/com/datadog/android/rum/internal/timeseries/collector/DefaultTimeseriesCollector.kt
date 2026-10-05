/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.collector

import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.internal.lifecycle.ProcessLifecycleMonitor
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.timeseries.Pipeline
import com.datadog.android.rum.internal.timeseries.PipelineFactory
import com.datadog.android.rum.internal.timeseries.TimeseriesCollector
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Coordinates the components responsible for observing collection state, creating pipelines, and collecting
 * timeseries data.
 *
 * Timeseries collection is allowed only while a session is active, the app is in the foreground,
 * and [RumContext] is available.
 *
 * ### Session activity
 *
 * A session can be started, stopped, expired, or renewed. When a session is renewed, callbacks from the
 * previous session can arrive after callbacks from the new one. As [DefaultTimeseriesCollector] is shared
 * across all sessions, `activeSessionId` prevents obsolete session stop and RUM context callbacks from
 * mutating the current state. Session activity updates arrive from a worker thread.
 *
 * ### Foreground state
 *
 * Here foreground means that the process has at least one started, and therefore visible, Activity.
 * This includes a paused Activity that remains visible in multi-window mode. The collector listens for
 * [ProcessLifecycleMonitor.Callback.onStarted] and [ProcessLifecycleMonitor.Callback.onStopped] updates, which
 * Android always delivers on the main thread.
 *
 * A stopped update is debounced because a configuration change can stop the old Activity shortly before
 * its replacement starts, without sending the app to the background. [onStopped] claims a [Gate] foreground
 * ticket before scheduling the delayed transition on [scheduledExecutorService]; [onStarted] applies its resume
 * immediately, which also claims a fresh ticket and thereby supersedes any pending debounced stop. Because the
 * ticket check and the state mutation happen atomically inside [Gate], a resume can never race a pending
 * debounced stop regardless of how many threads [scheduledExecutorService] uses.
 *
 * ### RUM context updates
 *
 * A RUM context is needed when creating timeseries events, such as
 * [com.datadog.android.rum.model.TimeseriesCpuEvent] and
 * [com.datadog.android.rum.model.TimeseriesMemoryEvent]. The gate therefore remains closed until
 * [onRumContextUpdate] provides a context. This method can be called for every RUM event, so context updates are not
 * synchronized. Foreground and session activity updates are synchronized because they also advance the gate
 * generation. See [Gate] for details.
 *
 * The overall flow is shown below:
 *
 * ```
 *                  ┌────────────╮
 *  C ─────────────▶│            │
 *                  │            │           ┌──▶──┐
 *  S ─────────────▶│ G(C, S, F) │──▷|1├──▶  ▲  L  ▼
 *                  │            │           └──◀──┘
 *  F ─────────────▶│            │
 *                  └────────────╯
 * ```
 *
 * - **C** - [com.datadog.android.rum.internal.domain.RumContext] updates ([onRumContextUpdate]).
 * - **S** - session activity ([onSessionStart]/[onSessionStop]).
 * - **F** - app visibility ([onStarted]/[onStopped]), where [onStopped] debounces the update by claiming a
 *   [Gate] ticket ([Gate.startBackgroundTransition]) and applying it after a delay ([Gate.setForeground]),
 *   so a short Activity recreation during a configuration change does not report the app as being in the
 *   background.
 * - **G(C, S, F)** - [Gate], which combines C, S, and the debounced F.
 * - **|1├** - a one-shot guard that starts collection exactly once for each generation allowed by [Gate]
 *   (`looperGeneration` in [tryStartCollection]).
 * - **L** - [Looper], which runs the periodic sampling loop for each pipeline and verifies before every sample that
 *   [Gate] is still open for the generation in which the loop started.
 */
internal class DefaultTimeseriesCollector(
    private val internalLogger: InternalLogger,
    private val scheduledExecutorService: ScheduledExecutorService,
    private val pipelinesFactory: PipelineFactory
) : TimeseriesCollector, ProcessLifecycleMonitor.Callback {

    private val stateGate = Gate()
    private var activeSessionId: String? = null
    private val looper = Looper(
        name = OPERATION_NAME_TIMESERIES_SAMPLING,
        gate = stateGate,
        internalLogger = internalLogger,
        scheduledExecutorService = scheduledExecutorService
    )
    private val looperGeneration = AtomicInteger(NO_GENERATION)

    // Volatile as it could be accessed from both main and worker threads
    @Volatile
    internal var pipelines: List<Pipeline<*>> = emptyList()
        private set

    @WorkerThread
    override fun onRumContextUpdate(newRumContext: RumContext) {
        if (newRumContext.sessionId != activeSessionId) return

        stateGate.setRumContext(newRumContext, onUpdated = ::tryStartCollection)
    }

    @WorkerThread
    override fun onSessionStart(sessionId: String, sessionType: RumSessionType) {
        if (sessionId == activeSessionId) return

        stopActiveSession()
        pipelines = pipelinesFactory.create(sessionType)
        activeSessionId = sessionId
        stateGate.setSessionActive(isActive = true, onUpdated = ::tryStartCollection)
    }

    @WorkerThread
    override fun onSessionStop(sessionId: String) {
        if (sessionId != activeSessionId) return

        stopActiveSession()
    }

    @MainThread
    override fun onStarted() {
        stateGate.setForeground(isForeground = true, onUpdated = ::tryStartCollection)
    }

    @MainThread
    override fun onStopped() {
        val rumContext = stateGate.getRumContext()

        if (rumContext == null) {
            // In case if collection is not possible we could flip foreground flag instantly.
            stateGate.setForeground(false)
            return
        }

        // Captured together with rumContext: pipelines is @Volatile and can be reassigned by
        // onSessionStart on a worker thread while this transition is pending, and flushPipelines
        // always reads the live field. Snapshotting here keeps rumContext paired with the
        // pipelines it was collected into, instead of flushing a renewed session's pipelines
        // tagged with the outgoing session's rumContext.
        val pipelinesSnapshot = pipelines

        // If collection is in progress, delay the transition so an Activity recreated by a configuration
        // change can reach onStarted without interrupting the sampling loop. The ticket is claimed now,
        // synchronously, so a resume in the meantime is guaranteed to supersede this stale transition.
        val transitionId = stateGate.startBackgroundTransition()
        scheduledExecutorService.scheduleSafe(
            OPERATION_NAME_BACKGROUND_TRANSITION,
            BACKGROUND_TRANSITION_DELAY,
            TimeUnit.MILLISECONDS,
            internalLogger
        ) {
            stateGate.setForeground(
                isForeground = false,
                transitionId = transitionId,
                onUpdated = { _, _ -> pipelinesSnapshot.forEach { it.flush(rumContext) } }
            )
        }
    }

    @MainThread
    override fun onPaused() = Unit

    @MainThread
    override fun onResumed() = Unit

    // @Suppress("unused") required here to avoid creating wrapping lambda in [onRumContextUpdate] method
    @AnyThread
    private fun tryStartCollection(generation: Int, @Suppress("unused") rumContext: RumContext) {
        if (looperGeneration.getAndSet(generation) != generation) {
            pipelines.forEach { pipeline ->
                looper.start(
                    generation,
                    pipeline.intervalMs,
                    TimeUnit.MILLISECONDS,
                    pipeline::execute
                )
            }
        }
    }

    // @Suppress("unused") required here to avoid creating wrapping lambdas at the call sites above
    @AnyThread
    private fun flushPipelines(@Suppress("unused") generation: Int, rumContext: RumContext) {
        pipelines.forEach { pipeline -> pipeline.flush(rumContext) }
    }

    @WorkerThread
    private fun stopActiveSession() {
        if (activeSessionId == null) return
        stateGate.setSessionActive(isActive = false, onUpdated = ::flushPipelines)
        activeSessionId = null
    }

    internal companion object {
        const val OPERATION_NAME_TIMESERIES_SAMPLING = "Timeseries sampling"
        const val OPERATION_NAME_BACKGROUND_TRANSITION = "Timeseries background transition"

        // Matches ActivityViewTrackingStrategy.STOP_VIEW_DELAY_MS. Both delays keep the previous
        // foreground state across short Activity recreation during configuration changes.
        const val BACKGROUND_TRANSITION_DELAY = 200L

        // Should only being used for initialization
        const val NO_GENERATION = -1
    }
}
