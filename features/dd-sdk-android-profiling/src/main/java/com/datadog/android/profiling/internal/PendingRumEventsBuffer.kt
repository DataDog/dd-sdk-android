/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import com.datadog.android.internal.profiling.ProfilerEvent

internal class PendingRumEventsBuffer {

    private val lock = Any()
    private val longTasks = mutableListOf<ProfilerEvent.RumLongTaskEvent>()
    private val anrEvents = mutableListOf<ProfilerEvent.RumAnrEvent>()

    val pendingLongTasks: List<ProfilerEvent.RumLongTaskEvent>
        get() = synchronized(lock) { longTasks.toList() }

    val pendingAnrEvents: List<ProfilerEvent.RumAnrEvent>
        get() = synchronized(lock) { anrEvents.toList() }

    fun add(event: ProfilerEvent.RumLongTaskEvent) {
        synchronized(lock) { longTasks.add(event) }
    }

    fun add(event: ProfilerEvent.RumAnrEvent) {
        synchronized(lock) { anrEvents.add(event) }
    }

    fun drain(): Snapshot = synchronized(lock) {
        val snapshot = Snapshot(longTasks.toList(), anrEvents.toList())
        longTasks.clear()
        anrEvents.clear()
        snapshot
    }

    fun clear() {
        synchronized(lock) {
            longTasks.clear()
            anrEvents.clear()
        }
    }

    data class Snapshot(
        val longTasks: List<ProfilerEvent.RumLongTaskEvent>,
        val anrEvents: List<ProfilerEvent.RumAnrEvent>
    )
}
