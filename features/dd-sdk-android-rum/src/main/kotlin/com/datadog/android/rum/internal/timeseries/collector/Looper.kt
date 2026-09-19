/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.timeseries.collector

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.rum.internal.domain.RumContext
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Runs a self-rescheduling sampling operation while [gate] allows its generation.
 *
 * [start] schedules a single execution after the requested interval. Once the operation finishes,
 * it schedules the next execution only if the same generation is still allowed. A session or
 * foreground transition advances the gate generation, causing callbacks from the previous
 * chain to stop without explicit cancellation.
 *
 * Operations run on [scheduledExecutorService]. Failures are logged and do not break the chain as
 * long as its generation remains active. The interval is measured after each operation completes,
 * so this behaves as a fixed-delay loop rather than a fixed-rate loop.
 */
internal class Looper(
    private val name: String,
    private val gate: Gate,
    private val internalLogger: InternalLogger,
    private val scheduledExecutorService: ScheduledExecutorService
) {

    fun start(generation: Int, interval: Long, unit: TimeUnit, operation: (rumContext: RumContext) -> Unit) {
        scheduledExecutorService.scheduleSafe(name, interval, unit, internalLogger) {
            // Defaults to true so a thrown operation (caught below, before the assignment
            // completes) still reschedules, matching the pre-refactor double-check behavior.
            var reschedule = true
            try {
                reschedule = gate.runIfGateOpened(generation, operation)
            } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
                internalLogger.log(
                    level = InternalLogger.Level.ERROR,
                    targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                    messageBuilder = { ERROR_SAMPLING_FAILED },
                    throwable = t
                )
            } finally {
                if (reschedule) start(generation, interval, unit, operation)
            }
        }
    }

    internal companion object {
        const val ERROR_SAMPLING_FAILED = "Timeseries sampling iteration failed; rescheduling next sample."
    }
}
