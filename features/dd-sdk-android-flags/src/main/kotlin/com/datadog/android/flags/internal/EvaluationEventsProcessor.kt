/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.internal.aggregation.AggregationKey
import com.datadog.android.flags.internal.aggregation.AggregationStats
import com.datadog.android.flags.internal.aggregation.DDContext
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.internal.time.TimeProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Processor for evaluation logging events.
 *
 * Aggregates evaluation events before sending them to reduce network traffic.
 * Evaluations with the same AggregationKey are aggregated together.
 * Events are flushed on: time interval, size limit, or shutdown.
 *
 * Thread-safe for concurrent processEvaluation() and flush() calls.
 *
 * @param writer the writer to send evaluation events to
 * @param timeProvider provides timestamps for evaluations
 * @param scheduledExecutor executor for scheduling periodic flushes
 * @param internalLogger logger for errors
 * @param flushIntervalMs interval between automatic flushes (default 10s)
 * @param maxAggregations maximum aggregations before automatic flush (default 1000)
 */
internal class EvaluationEventsProcessor(
    private val writer: EvaluationEventWriter,
    private val timeProvider: TimeProvider,
    private val scheduledExecutor: ScheduledExecutorService,
    private val internalLogger: InternalLogger,
    private val flushIntervalMs: Long = DEFAULT_FLUSH_INTERVAL_MS,
    private val maxAggregations: Int = MAX_AGGREGATIONS_BEFORE_FLUSH
) {

    @Volatile
    private var aggregationMap = ConcurrentHashMap<AggregationKey, AggregationStats>()
    private val flushLock = Object()

    // Atomic flag to prevent concurrent flush operations
    private val flushInProgress = AtomicBoolean(false)

    @Volatile
    private var scheduledFlushFuture: ScheduledFuture<*>? = null

    /**
     * Processes an evaluation that has flag data.
     *
     * Evaluations with the same aggregation key are grouped together.
     * Flush triggered if size limit reached.
     *
     * Thread-safe: can be called concurrently from multiple threads.
     *
     * @param flagKey the flag key
     * @param context the evaluation context (targeting key, attributes)
     * @param ddContext the DD SDK context (including RUM fields such as view ID, application ID)
     * @param variantKey the variant/variation key, or null if not assigned
     * @param allocationKey the allocation key, or null if not assigned
     * @param reason the resolution reason indicating why this value was resolved
     * @param errorCode the error code, or null if not applicable
     * @param errorMessage the optional error message for debugging
     */
    fun processEvaluation(
        flagKey: String,
        context: EvaluationContext,
        ddContext: DDContext,
        variantKey: String?,
        allocationKey: String?,
        reason: String?,
        errorCode: String?,
        errorMessage: String?
    ) {
        val timestamp = timeProvider.getDeviceTimestampMillis()

        val key = AggregationKey(
            flagKey = flagKey,
            variantKey = variantKey,
            allocationKey = allocationKey,
            targetingKey = context.targetingKey,
            rumViewId = ddContext.viewId,
            errorCode = errorCode
        )

        @Suppress("UnsafeThirdPartyFunctionCall") // Only throws if null is passed
        val existing = aggregationMap.putIfAbsent(
            key,
            AggregationStats(key, timestamp, context, ddContext, reason, errorMessage)
        )

        // Pre-existing stats object found, record evaluation
        existing?.recordEvaluation(timestamp, errorMessage)

        // Flush when buffer is full
        if (aggregationMap.size >= maxAggregations) {
            flush()
        }
    }

    /**
     * Flushes all aggregated evaluations.
     *
     * Cancels any scheduled flush and schedules a new one upon completion.
     * Thread-safe: atomic flag prevents concurrent flush operations.
     * If a flush is already in progress, this method returns immediately (non-blocking).
     * New evaluations can be processed concurrently with flush operations.
     */
    fun flush(rescheduleFlush: Boolean = true) {
        if (!flushInProgress.compareAndSet(false, true)) {
            return
        }

        // Cancel any scheduled flush tasks
        scheduledFlushFuture?.cancel(false)

        try {
            val snapshot = synchronized(flushLock) {
                if (aggregationMap.isEmpty()) {
                    return@synchronized null
                }
                val entriesToFlush = aggregationMap
                aggregationMap = ConcurrentHashMap()
                entriesToFlush
            } ?: return

            // Convert to events and write (to storage)
            writer.writeAll(
                snapshot.map { (_, stats) ->
                    stats.toEvaluationEvent()
                }
            )
        } finally {
            flushInProgress.set(false)
            if (rescheduleFlush) {
                schedulePeriodicFlush()
            }
        }
    }

    /**
     * Schedules periodic flushing of aggregated evaluations.
     *
     * Flushes occur at the configured time interval (default 10s).
     */
    fun schedulePeriodicFlush() {
        try {
            @Suppress("UnsafeThirdPartyFunctionCall") // exception caught below
            scheduledFlushFuture = scheduledExecutor.schedule(
                {
                    flush()
                },
                flushIntervalMs,
                TimeUnit.MILLISECONDS
            )
        } catch (e: RejectedExecutionException) {
            // Executor is shut down or cannot accept task
            // Expected during processor shutdown, log for diagnostics
            internalLogger.log(
                InternalLogger.Level.WARN,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { "Failed to schedule evaluation flush" },
                e
            )
        }
    }

    /**
     * Stops the processor and flushes remaining evaluations.
     *
     * Performs final flush on shutdown.
     * Shuts down the scheduled executor to prevent race conditions.
     * Cancels any scheduled flush tasks.
     *
     * Thread-safe: can be called concurrently with other operations.
     */
    fun stop() {
        // Shutdown executor FIRST to reject any new schedule attempts
        // This prevents the race condition where a flush could reschedule a task after we cancel the future
        @Suppress("UnsafeThirdPartyFunctionCall") // safe - does not throw in Android
        scheduledExecutor.shutdown()

        // Cancel currently scheduled future; don't interrupt if it's running
        scheduledFlushFuture?.cancel(false)

        // Perform final flush
        flush(rescheduleFlush = false)

        // Wait for executor to terminate gracefully
        try {
            @Suppress("UnsafeThirdPartyFunctionCall") // InterruptedException is caught and handled
            if (!scheduledExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                // Force shutdown if tasks don't complete in time
                scheduledExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            scheduledExecutor.shutdownNow()
            @Suppress("UnsafeThirdPartyFunctionCall") // safe - SecurityException not thrown in Android
            Thread.currentThread().interrupt()
        }
    }

    companion object {
        const val DEFAULT_FLUSH_INTERVAL_MS = 10_000L // 10 seconds
        const val MIN_FLUSH_INTERVAL_MS = 1_000L
        const val MAX_FLUSH_INTERVAL_MS = 60_000L
        const val MAX_AGGREGATIONS_BEFORE_FLUSH = 1000
    }
}
