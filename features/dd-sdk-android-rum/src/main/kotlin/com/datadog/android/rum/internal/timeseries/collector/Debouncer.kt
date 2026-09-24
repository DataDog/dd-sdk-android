/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.timeseries.collector

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.scheduleSafe
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Keeps at most one delayed [call][runDelayed] scheduled on [scheduledExecutorService].
 *
 * [runDelayed] and [cancel] can be called from any thread. Rather than cancelling a scheduled task
 * explicitly, each call to [runDelayed] or [cancel] advances a generation counter; the scheduled task
 * captures the generation current at the time it was posted and only runs [call] if that generation is
 * still current when it fires. A later [runDelayed] or [cancel] transparently supersedes it. This is the
 * same obsolete-callback rejection [Looper] uses for [Gate].
 */
internal class Debouncer(
    private val scheduledExecutorService: ScheduledExecutorService,
    private val internalLogger: InternalLogger,
    private val operationName: String
) {
    private val generation = AtomicInteger(NO_GENERATION)

    fun runDelayed(delayMs: Long, call: () -> Unit) {
        val transitionGeneration = generation.incrementAndGet()
        scheduledExecutorService.scheduleSafe(operationName, delayMs, TimeUnit.MILLISECONDS, internalLogger) {
            if (generation.get() == transitionGeneration) call()
        }
    }

    fun cancel() {
        generation.incrementAndGet()
    }

    internal companion object {
        // Should only be used for initialization
        const val NO_GENERATION = -1
    }
}
