/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries

import androidx.annotation.AnyThread
import androidx.collection.LruCache

/** Tracks sessions with collected timeseries data points, shared across all [Pipeline]s. */
internal class CollectedTimeseriesSessionRegistry(size: Int = MAX_SESSIONS) {
    // Written from the sampling threads and read from the RUM worker thread. Keeps only the [MAX_SESSIONS]
    // most recently used sessions: older ones produce no RUM events anymore, so they evict themselves.
    @Suppress("UnsafeThirdPartyFunctionCall") // MAX_SESSIONS is positive
    private val sessionsWithCollectedTimeseries = LruCache<String, Unit>(size)

    @AnyThread
    fun setTimeseriesCollected(sessionId: String) {
        @Suppress("UnsafeThirdPartyFunctionCall") // key and value are non-null
        sessionsWithCollectedTimeseries.put(sessionId, Unit)
    }

    /** Whether at least one timeseries data point was collected for the session with [sessionId]. */
    @AnyThread
    fun isTimeseriesCollected(sessionId: String): Boolean = sessionsWithCollectedTimeseries[sessionId] != null

    internal companion object {
        const val MAX_SESSIONS = 3
    }
}
