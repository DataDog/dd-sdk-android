/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.configuration.BackPressureMitigation
import com.datadog.android.core.configuration.BackPressureStrategy
import com.datadog.android.internal.thread.NamedExecutionUnit
import java.util.concurrent.TimeUnit

/**
 * [LinkedBlockingQueue] that supports backpressure handling via the chosen backpressure mitigation strategy.
 *
 * This queue may be either bounded or unbounded by specifying capacity. See docs of [LinkedBlockingQueue] for more
 * details.
 *
 * If queue is unbounded, there is still a possibility to be notified if certain size threshold is reached.
 */
internal class BackPressuredBlockingQueue<E : Any> : ObservableLinkedBlockingQueue<E> {

    private val logger: InternalLogger
    private val executorContext: String
    internal val capacity: Int
    private val notifyThreshold: Int
    private val onThresholdReached: () -> Unit
    private val onItemDropped: (Any) -> Unit
    private val backpressureMitigation: BackPressureMitigation?

    constructor(
        logger: InternalLogger,
        executorContext: String,
        backPressureStrategy: BackPressureStrategy
    ) : this(
        logger,
        executorContext,
        backPressureStrategy.capacity,
        backPressureStrategy.capacity,
        backPressureStrategy.onThresholdReached,
        backPressureStrategy.onItemDropped,
        backPressureStrategy.backpressureMitigation
    )

    constructor(
        logger: InternalLogger,
        executorContext: String,
        notifyThreshold: Int,
        capacity: Int,
        onThresholdReached: () -> Unit,
        onItemDropped: (Any) -> Unit,
        backpressureMitigation: BackPressureMitigation?
    ) : super(capacity) {
        this.logger = logger
        this.executorContext = executorContext
        this.capacity = capacity
        this.notifyThreshold = notifyThreshold
        this.onThresholdReached = onThresholdReached
        this.onItemDropped = onItemDropped
        this.backpressureMitigation = backpressureMitigation
    }

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
            if (size == notifyThreshold) {
                notifyThresholdReached()
            }
            return true
        }
    }

    override fun put(e: E) {
        if (size + 1 == notifyThreshold) {
            notifyThresholdReached()
        }
        super.put(e)
    }

    private fun addWithBackPressure(
        e: E,
        operation: (E) -> Boolean
    ): Boolean {
        val remainingCapacity = remainingCapacity()
        return if (remainingCapacity == 0) {
            when (backpressureMitigation) {
                BackPressureMitigation.DROP_OLDEST -> {
                    val first = take()
                    notifyItemDropped(first)
                    operation(e)
                }

                BackPressureMitigation.IGNORE_NEWEST, null -> {
                    notifyItemDropped(e)
                    true
                }
            }
        } else {
            if (size + 1 == notifyThreshold) {
                notifyThresholdReached()
            }
            operation(e)
        }
    }

    private fun notifyThresholdReached() {
        val dump = dumpQueue()
        val backPressureMap = buildMap {
            put("capacity", capacity)
            if (!dump.isNullOrEmpty()) {
                put("dump", dump)
            }
        }
        onThresholdReached()
        logger.log(
            level = InternalLogger.Level.WARN,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            messageBuilder = { "BackPressuredBlockingQueue reached capacity:$notifyThreshold" },
            throwable = null,
            onlyOnce = false,
            additionalProperties = mapOf(
                "backpressure" to backPressureMap,
                "executor.context" to executorContext
            )
        )
    }

    private fun notifyItemDropped(item: E) {
        onItemDropped(item)
        val name = (item as? NamedExecutionUnit)?.name ?: item.toString()
        // Note, do not send this to telemetry as it might cause a stack overflow
        logger.log(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.MAINTAINER,
            messageBuilder = { "Dropped item in BackPressuredBlockingQueue queue: $name" },
            throwable = null,
            onlyOnce = false,
            additionalProperties = mapOf(
                "backpressure.capacity" to capacity,
                "executor.context" to executorContext
            )
        )
    }
}
