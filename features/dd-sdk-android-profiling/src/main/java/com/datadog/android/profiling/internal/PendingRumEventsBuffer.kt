/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import com.datadog.android.internal.profiling.ProfilerEvent
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal class PendingRumEventsBuffer {

    private val lock = ReentrantReadWriteLock()
    private val longTasks = mutableListOf<ProfilerEvent.RumLongTaskEvent>()
    private val anrEvents = mutableListOf<ProfilerEvent.RumAnrEvent>()
    private val vitalEvents = mutableListOf<ProfilerEvent.RumVitalEvent>()

    val pendingLongTasks: List<ProfilerEvent.RumLongTaskEvent>
        get() = lock.read { longTasks.toList() }

    val pendingAnrEvents: List<ProfilerEvent.RumAnrEvent>
        get() = lock.read { anrEvents.toList() }

    val pendingVitalEvents: List<ProfilerEvent.RumVitalEvent>
        get() = lock.read { vitalEvents.toList() }

    fun add(event: ProfilerEvent.RumLongTaskEvent) {
        lock.write { longTasks.add(event) }
    }

    fun add(event: ProfilerEvent.RumAnrEvent) {
        lock.write { anrEvents.add(event) }
    }

    fun add(event: ProfilerEvent.RumVitalEvent) {
        lock.write { vitalEvents.add(event) }
    }

    fun drain(): Snapshot = lock.write {
        val snapshot = Snapshot(longTasks.toList(), anrEvents.toList(), vitalEvents.toList())
        longTasks.clear()
        anrEvents.clear()
        vitalEvents.clear()
        snapshot
    }

    fun clear() {
        lock.write {
            longTasks.clear()
            anrEvents.clear()
            vitalEvents.clear()
        }
    }

    data class Snapshot(
        val longTasks: List<ProfilerEvent.RumLongTaskEvent>,
        val anrEvents: List<ProfilerEvent.RumAnrEvent>,
        val vitalEvents: List<ProfilerEvent.RumVitalEvent>
    )
}
