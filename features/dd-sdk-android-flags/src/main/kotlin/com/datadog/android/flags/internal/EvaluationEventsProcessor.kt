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
 * Aggregates flag evaluation events before writing them to storage.
 *
 * Thread-safe for concurrent [processEvaluation] and [flush] calls.
 */
internal class EvaluationEventsProcessor(
    private val writer: EvaluationEventWriter,
    private val timeProvider: TimeProvider,
    private val scheduledExecutor: ScheduledExecutorService,
    private val internalLogger: InternalLogger,
    private val flushIntervalMs: Long,
    private val maxAggregations: Int
) {

    @Volatile
    private var aggregationMap = ConcurrentHashMap<AggregationKey, AggregationStats>()
    private val flushLock = Object()

    private val flushInProgress = AtomicBoolean(false)

    @Volatile
    private var scheduledFlushFuture: ScheduledFuture<*>? = null

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
            viewName = ddContext.viewName,
            errorCode = errorCode
        )

        @Suppress("UnsafeThirdPartyFunctionCall") // Only throws if null is passed
        val existing = aggregationMap.putIfAbsent(
            key,
            AggregationStats(key, timestamp, context, ddContext, reason, errorMessage)
        )

        existing?.recordEvaluation(timestamp, errorMessage)

        if (aggregationMap.size >= maxAggregations) {
            flush(true)
        }
    }

    /**
     * Flushes aggregated evaluations. Non-blocking if flush already in progress.
     */
    fun flush(rescheduleFlush: Boolean = false) {
        if (!flushInProgress.compareAndSet(false, true)) {
            return
        }

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

    fun schedulePeriodicFlush() {
        try {
            @Suppress("UnsafeThirdPartyFunctionCall") // exception caught below
            scheduledFlushFuture = scheduledExecutor.schedule(
                {
                    flush(true)
                },
                flushIntervalMs,
                TimeUnit.MILLISECONDS
            )
        } catch (e: RejectedExecutionException) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { "Failed to schedule evaluation flush" },
                e
            )
        }
    }

    fun stop() {
        @Suppress("UnsafeThirdPartyFunctionCall") // safe - does not throw in Android
        scheduledExecutor.shutdown()
        scheduledFlushFuture?.cancel(false)
        flush(rescheduleFlush = false)

        try {
            @Suppress("UnsafeThirdPartyFunctionCall") // InterruptedException is caught and handled
            if (!scheduledExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            scheduledExecutor.shutdownNow()
            @Suppress("UnsafeThirdPartyFunctionCall") // safe - SecurityException not thrown in Android
            Thread.currentThread().interrupt()
        }
    }
}
