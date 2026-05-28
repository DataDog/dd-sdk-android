/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.heatmaps

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Internal — construct via [HeatmapIdentifierRegistry.create].
 *
 * Writes (one per Session Replay traversal) atomically replace the entire snapshot so reads
 * always see a complete, consistent map — never a partial update. Reads are non-blocking
 * with respect to other reads.
 *
 * Note: there is no guarantee that a read sees the snapshot that was current at the exact
 * moment of a tap. If SR writes a new snapshot between the tap and the RUM lookup, the read
 * returns the newer snapshot. The screen name guard ensures a stale cross-screen snapshot
 * returns null — the worst case is a missed tap, not a wrong one.
 */
internal class HeatmapIdentifierStore : HeatmapIdentifierRegistry {

    private val lock = ReentrantReadWriteLock()

    private var snapshotScreenName: String? = null
    private val identifiers: MutableMap<Long, HeatmapIdentifier> = mutableMapOf()

    override fun setHeatmapIdentifiers(identifiers: Map<Long, HeatmapIdentifier>, screenName: String) {
        lock.write {
            this.snapshotScreenName = screenName
            this.identifiers.clear()
            this.identifiers.putAll(identifiers)
        }
    }

    override fun getHeatmapIdentifier(heatmapViewKey: Long, currentScreenName: String): HeatmapIdentifier? {
        return lock.read {
            if (snapshotScreenName == currentScreenName) identifiers[heatmapViewKey] else null
        }
    }
}
