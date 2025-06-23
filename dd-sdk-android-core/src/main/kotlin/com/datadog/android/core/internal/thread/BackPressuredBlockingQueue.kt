/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.configuration.BackPressureMitigation
import com.datadog.android.core.configuration.BackPressureStrategy
import com.datadog.android.internal.thread.NamedRunnable
import java.util.concurrent.TimeUnit

internal class BackPressuredBlockingQueue<E : Any>(
    private val logger: InternalLogger,
    private val executorContext: String,
    private val backPressureStrategy: BackPressureStrategy
) : ObservableLinkedBlockingQueue<E>(
    backPressureStrategy.capacity
) {

    override fun offer(e: E): Boolean {
        return addWithBackPressure(e) {
            @Suppress("UnsafeThirdPartyFunctionCall") // can't have NPE here
            super.offer(it)
        }
    }

    override fun offer(e: E, timeout: Long, unit: TimeUnit?): Boolean {
        @Suppress("UnsafeThirdPartyFunctionCall") // can't have NPE here
        val accepted = super.offer(e, timeout, unit)
        if (!accepted) {
            return offer(e)
        } else {
            if (remainingCapacity() == 0) {
                onThresholdReached()
            }
            return true
        }
    }

    private fun addWithBackPressure(
        e: E,
        operation: (E) -> Boolean
    ): Boolean {
        val remainingCapacity = remainingCapacity()
        return if (remainingCapacity == 0) {
            when (backPressureStrategy.backpressureMitigation) {
                BackPressureMitigation.DROP_OLDEST -> {
                    val first = take()
                    onItemDropped(first)
                    operation(e)
                }

                BackPressureMitigation.IGNORE_NEWEST -> {
                    onItemDropped(e)
                    true
                }
            }
        } else {
            if (remainingCapacity == 1) {
                onThresholdReached()
            }
            operation(e)
        }
    }

    private fun onThresholdReached() {
        val dump = dumpQueue()
        val backPressureMap = buildMap {
            put("capacity", backPressureStrategy.capacity)
            if (!dump.isNullOrEmpty()) {
                put("dump", dump)
            }
        }
        backPressureStrategy.onThresholdReached()
        logger.log(
            level = InternalLogger.Level.WARN,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            messageBuilder = { "BackPressuredBlockingQueue reached capacity:${backPressureStrategy.capacity}" },
            throwable = null,
            onlyOnce = false,
            additionalProperties = mapOf(
                "backpressure" to backPressureMap,
                "executor.context" to executorContext
            )
        )
    }

    private fun onItemDropped(item: E) {
        backPressureStrategy.onItemDropped(item)
        val name = (item as? NamedRunnable)?.sanitizedName ?: item.toString()
        // Note, do not send this to telemetry as it might cause a stack overflow
        logger.log(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.MAINTAINER,
            messageBuilder = { "Dropped item in BackPressuredBlockingQueue queue: $name" },
            throwable = null,
            onlyOnce = false,
            additionalProperties = mapOf(
                "backpressure.capacity" to backPressureStrategy.capacity,
                "executor.context" to executorContext
            )
        )
    }
}
