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
 * Writes (one per Session Replay traversal) atomically replace the snapshot; reads
 * (per RUM tap action) are non-blocking with respect to other reads.
 */
internal class HeatmapIdentifierStore : HeatmapIdentifierRegistry {

    private val lock = ReentrantReadWriteLock()

    private var identifiers: Map<Long, HeatmapIdentifier> = HashMap()

    override fun setHeatmapIdentifiers(identifiers: Map<Long, HeatmapIdentifier>) {
        // HashMap(int) throws IllegalArgumentException only for a negative argument;
        // putAll() throws NullPointerException only for a null map. Map.size is non-negative
        // and the parameter is non-null, so neither throws here.
        @Suppress("UnsafeThirdPartyFunctionCall")
        val snapshot: Map<Long, HeatmapIdentifier> = HashMap<Long, HeatmapIdentifier>(identifiers.size).apply {
            putAll(identifiers)
        }
        lock.write {
            this.identifiers = snapshot
        }
    }

    override fun heatmapIdentifier(viewId: Long): HeatmapIdentifier? {
        return lock.read {
            identifiers[viewId]
        }
    }
}
