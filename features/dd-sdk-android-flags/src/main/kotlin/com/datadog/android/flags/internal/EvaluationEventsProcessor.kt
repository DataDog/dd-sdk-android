/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.internal.aggregation.EvaluationAggregationStats
import com.datadog.android.flags.internal.aggregation.EvaluationAggregator
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.internal.time.TimeProvider
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Aggregates flag evaluation events before writing them to storage.
 */
internal class EvaluationEventsProcessor(
    private val writer: EvaluationEventWriter,
    private val timeProvider: TimeProvider,
    private val scheduledExecutor: ScheduledExecutorService,
    private val internalLogger: InternalLogger,
    private val flushIntervalMs: Long,
    private val aggregator: EvaluationAggregator,
    periodicFlushEnabled: Boolean = true
) {
    private val flushMutex = ReentrantLock()
    private var scheduledFlushFuture: ScheduledFuture<*>? = null

    @Volatile
    private var lastFlushTimeMs: Long = 0L

    init {
        if (periodicFlushEnabled) {
            startPeriodicFlush()
        }
    }

    fun processEvaluation(
        flagKey: String,
        context: EvaluationContext,
        rumApplicationId: String?,
        rumViewName: String?,
        variantKey: String?,
        allocationKey: String?,
        errorCode: String?,
        errorMessage: String?
    ) {
        val drainedEvents = aggregator.record(
            timestamp = timeProvider.getDeviceTimestampMillis(),
            flagKey = flagKey,
            context = context,
            rumApplicationId = rumApplicationId,
            rumViewName = rumViewName,
            variantKey = variantKey,
            allocationKey = allocationKey,
            errorCode = errorCode,
            errorMessage = errorMessage
        )

        if (drainedEvents.isNotEmpty()) {
            lastFlushTimeMs = timeProvider.getDeviceTimestampMillis()
            writer.writeAll(drainedEvents)
        }
    }

    fun flush() {
        if (!flushMutex.tryLock()) {
            return
        }

        val events: List<EvaluationAggregationStats>
        try {
            events = aggregator.drain()
            lastFlushTimeMs = timeProvider.getDeviceTimestampMillis()
        } finally {
            @Suppress("UnsafeThirdPartyFunctionCall") // safe - only called after successful tryLock()
            flushMutex.unlock()
        }

        if (events.isNotEmpty()) {
            writer.writeAll(events)
        }
    }

    private fun startPeriodicFlush() {
        flushMutex.withLock {
            val currentFuture = scheduledFlushFuture
            if (currentFuture == null || currentFuture.isCancelled || currentFuture.isDone) {
                try {
                    @Suppress("UnsafeThirdPartyFunctionCall") // exception caught below
                    scheduledFlushFuture = scheduledExecutor.scheduleAtFixedRate(
                        { periodicFlushTask() },
                        flushIntervalMs,
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
        }
    }

    private fun periodicFlushTask() {
        val now = timeProvider.getDeviceTimestampMillis()
        if (now - lastFlushTimeMs >= flushIntervalMs) {
            flush()
        }
    }

    fun stop() {
        @Suppress("UnsafeThirdPartyFunctionCall") // safe - does not throw in Android
        scheduledExecutor.shutdown()

        // Wait for any in-progress flush to complete, then drain any remaining events.
        val events = flushMutex.withLock {
            scheduledFlushFuture?.cancel(false)
            aggregator.drain()
            lastFlushTimeMs = timeProvider.getDeviceTimestampMillis()
        }

        if (events.isNotEmpty()) {
            writer.writeAll(events)
        }

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
