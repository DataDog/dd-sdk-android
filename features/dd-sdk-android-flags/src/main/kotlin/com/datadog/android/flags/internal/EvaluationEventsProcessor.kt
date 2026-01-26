/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.internal.aggregation.AggregationKey
import com.datadog.android.flags.internal.aggregation.AggregationStats
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.internal.time.TimeProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Processor for evaluation logging events.
 *
 * Aggregates evaluation events before sending them to reduce network traffic.
 * Evaluations with the same AggregationKey are aggregated together.
 * Events are flushed on: time interval, size limit, or shutdown.
 * The aggregation map is cleared after each flush.
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

    private val aggregationMap = ConcurrentHashMap<AggregationKey, AggregationStats>()

    @Volatile
    private var scheduledFlushFuture: ScheduledFuture<*>? = null

    /**
     * Processes an evaluation that has flag data.
     *
     * This is the primary path for regular flag evaluations where we have
     * complete flag metadata from the repository including variant, allocation,
     * and resolution reason.
     *
     * Evaluations with the same aggregation key are grouped together.
     * Automatic flush triggered if size limit reached.
     *
     * Thread-safe: can be called concurrently from multiple threads.
     *
     * @param flagKey the flag key
     * @param context the evaluation context (targeting key, attributes)
     * @param variantKey the variant/variation key, or null if not assigned
     * @param allocationKey the allocation key, or null if not assigned
     * @param reason the resolution reason indicating why this value was resolved
     */
    fun processSuccessEvaluation(
        flagKey: String,
        context: EvaluationContext,
        variantKey: String?,
        allocationKey: String?,
        reason: String
    ) {
        recordEvaluation(
            flagKey = flagKey,
            context = context,
            variantKey = variantKey,
            allocationKey = allocationKey,
            reason = reason,
            errorCode = null,
            errorMessage = null
        )
    }

    /**
     * Processes an error evaluation without any flag data.
     *
     * This path is used when flag resolution fails before we can retrieve
     * any flag data from the repository (e.g., SDK not initialized, network error,
     * flag not found, provider not ready).
     *
     * Without flag data, we aggregate only on the error code.
     *
     * Evaluations with the same aggregation key are grouped together.
     * Automatic flush triggered if size limit reached.
     *
     * Thread-safe: can be called concurrently from multiple threads.
     *
     * @param flagKey the flag key
     * @param context the evaluation context (targeting key, attributes)
     * @param errorCode the error type (for aggregation)
     * @param errorMessage the detailed error message (for logging)
     */
    fun processErrorEvaluation(
        flagKey: String,
        context: EvaluationContext,
        errorCode: String,
        errorMessage: String
    ) {
        recordEvaluation(
            flagKey = flagKey,
            context = context,
            variantKey = null,
            allocationKey = null,
            reason = null,
            errorCode = errorCode,
            errorMessage = errorMessage
        )
    }

    /**
     * Internal method that handles evaluation recording.
     *
     * Creates the aggregation key and updates the stats.
     *
     * @param flagKey the flag key being evaluated
     * @param context the evaluation context
     * @param variantKey the variant key, null if not applicable
     * @param allocationKey the allocation key, null if not applicable
     * @param reason the resolution reason, null if error without flag data
     * @param errorCode the error code, null if successful evaluation
     * @param errorMessage optional error message for debugging
     */
    private fun recordEvaluation(
        flagKey: String,
        context: EvaluationContext,
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
            errorCode = errorCode
        )

        // Update existing stats or create new ones
        synchronized(aggregationMap) {
            val existing = aggregationMap[key]
            if (existing != null) {
                existing.recordEvaluation(timestamp, errorMessage)
            } else {
                aggregationMap[key] = AggregationStats(timestamp, context, reason, errorMessage)
            }
        }

        // Size-based flush
        if (aggregationMap.size >= maxAggregations) {
            flush()
        }
    }

    /**
     * Flushes all aggregated evaluations.
     *
     * Called on: time interval, size limit, or shutdown.
     * The aggregation map is cleared after flushing.
     *
     * Thread-safe: synchronized to prevent concurrent flush operations.
     */
    fun flush() {
        // Take atomic snapshot and clear map
        val snapshot = synchronized(aggregationMap) {
            val copy = aggregationMap.toMap()
            aggregationMap.clear()
            copy
        }

        if (snapshot.isEmpty()) {
            return
        }

        // Convert each aggregation to an event and write
        snapshot.forEach { (key, stats) ->
            val event = stats.toEvaluationEvent(key.flagKey, key)
            writer.write(event)
        }
    }

    /**
     * Schedules periodic flushing of aggregated evaluations.
     *
     * Flushes occur at the configured time interval (default 10s).
     * The task reschedules itself after each flush (self-scheduling pattern).
     */
    fun schedulePeriodicFlush() {
        try {
            @Suppress("UnsafeThirdPartyFunctionCall") // exception caught below
            scheduledFlushFuture = scheduledExecutor.schedule(
                {
                    flush()
                    schedulePeriodicFlush() // Self-reschedule
                },
                flushIntervalMs,
                TimeUnit.MILLISECONDS
            )
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Catch all exceptions during scheduling to prevent crashes
            internalLogger.log(
                InternalLogger.Level.ERROR,
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
        // This prevents the race condition where a scheduled task could reschedule itself
        // after we cancel the future
        @Suppress("UnsafeThirdPartyFunctionCall") // safe - does not throw in Android
        scheduledExecutor.shutdown()

        // Cancel currently scheduled future (may already be running)
        scheduledFlushFuture?.cancel(false)

        // Perform final flush
        flush()

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
