/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.api.InternalLogger
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
    @Volatile private var periodicFlushEnabled: Boolean = true
) {
    private val flushMutex = ReentrantLock()

    @Volatile
    private var scheduledFlushFuture: ScheduledFuture<*>? = null
    private val schedulerMutex = ReentrantLock()

    init {
        reschedulePeriodicFlush()
    }

    fun processEvaluation(
        flagKey: String,
        context: EvaluationContext,
        service: String?,
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
            service = service,
            rumApplicationId = rumApplicationId,
            rumViewName = rumViewName,
            variantKey = variantKey,
            allocationKey = allocationKey,
            errorCode = errorCode,
            errorMessage = errorMessage
        )

        if (drainedEvents != null) {
            // If no flush is in process, restart the periodic flush schedule.
            if (flushMutex.tryLock()) {
                try {
                    reschedulePeriodicFlush()
                } finally {
                    @Suppress("UnsafeThirdPartyFunctionCall") // safe - called after lock()
                    flushMutex.unlock()
                }
            }

            writer.writeAll(drainedEvents)
        }
    }

    fun flush() {
        if (!flushMutex.tryLock()) {
            return
        }

        try {
            flushInternal()
        } finally {
            @Suppress("UnsafeThirdPartyFunctionCall") // safe - only called after successful tryLock()
            flushMutex.unlock()
        }
    }

    private fun flushInternal() {
        val events = aggregator.drain()

        reschedulePeriodicFlush()

        if (events.isNotEmpty()) {
            writer.writeAll(events)
        }
    }

    fun reschedulePeriodicFlush() {
        if (!periodicFlushEnabled) {
            return
        }

        // Cancel any scheduled before scheduling a new one.
        @Suppress("UnsafeThirdPartyFunctionCall") // safe - ReentrantLock.lock() does not throw
        schedulerMutex.withLock {
            scheduledFlushFuture?.cancel(false)
            try {
                @Suppress("UnsafeThirdPartyFunctionCall") // exception caught below
                scheduledFlushFuture = scheduledExecutor.schedule(
                    { flush() },
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

    fun stop() {
        periodicFlushEnabled = false

        @Suppress("UnsafeThirdPartyFunctionCall") // safe - does not throw in Android
        scheduledExecutor.shutdown()
        scheduledFlushFuture?.cancel(false)

        @Suppress("UnsafeThirdPartyFunctionCall") // safe - ReentrantLock.lock() does not throw
        // Wait for any in-progress flush to complete, then flush any remaining events.
        flushMutex.withLock {
            flushInternal()
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
