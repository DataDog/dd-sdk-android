/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.internal.thread.NamedExecutionUnit
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

internal open class ObservableLinkedBlockingQueue<E : Any> : LinkedBlockingQueue<E> {

    private val currentTimeProvider: () -> Long

    constructor(
        currentTimeProvider: () -> Long = { System.currentTimeMillis() }
    ) : this(Int.MAX_VALUE, currentTimeProvider)

    constructor(
        capacity: Int,
        currentTimeProvider: () -> Long = { System.currentTimeMillis() }
    ) : super(capacity) {
        this.currentTimeProvider = currentTimeProvider
    }

    private val lastDumpTimestamp: AtomicLong = AtomicLong(0)

    fun dumpQueue(): Map<String, Int>? {
        val currentTime = currentTimeProvider.invoke()
        val last = lastDumpTimestamp.get()
        val timeSinceLastDump = currentTime - last
        return if (timeSinceLastDump > DUMPING_TIME_INTERVAL_IN_MS) {
            if (lastDumpTimestamp.compareAndSet(last, currentTime)) {
                buildDumpMap()
            } else {
                null
            }
        } else {
            null
        }
    }

    private fun buildDumpMap(): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        super.toArray().forEach { runnable ->
            (runnable as? NamedExecutionUnit)?.name?.let {
                map.put(it, (map[it] ?: 0) + 1)
            }
        }
        return map
    }

    companion object {
        private val DUMPING_TIME_INTERVAL_IN_MS = TimeUnit.SECONDS.toMillis(5)
    }
}
