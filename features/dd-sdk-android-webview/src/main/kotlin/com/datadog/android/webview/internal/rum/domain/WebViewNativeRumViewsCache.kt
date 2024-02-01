/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.rum.domain

import java.util.LinkedList
import java.util.concurrent.TimeUnit

internal class WebViewNativeRumViewsCache(
    private val entriesTtlLimitInMs: Long = DATA_PURGE_TTL_LIMIT_IN_MS
) : NativeRumViewsCache {

    internal val parentViewsHistoryQueue: LinkedList<ViewEntry> = LinkedList()

    @Synchronized
    override fun resolveLastParentIdForBrowserEvent(browserEventTimestampInMs: Long): String? {
        // iterate the history stack to find the first entry that is older than the current event
        // and hasReplay == true
        // in case we do not find one return the first candidate that is older than the current event
        val iterator = parentViewsHistoryQueue.iterator()
        var backupCandidate: String? = null
        while (iterator.hasNext()) {
            // the function is synchronized and we are checking hasNext() before calling next()
            @Suppress("UnsafeThirdPartyFunctionCall")
            val entry = iterator.next()
            if (entry.timestamp <= browserEventTimestampInMs) {
                if (backupCandidate == null) {
                    backupCandidate = entry.viewId
                }
                if (entry.hasReplay) {
                    return entry.viewId
                }
            }
        }
        return backupCandidate
    }

    @Synchronized
    override fun addToCache(event: Map<String, Any?>) {
        val activeViewId = event[VIEW_ID_KEY] as? String
        val eventTimestamp = event[VIEW_TIMESTAMP_KEY] as? Long
        val hasReplay = event[VIEW_HAS_REPLAY_KEY] as? Boolean ?: false

        if (activeViewId != null &&
            activeViewId != RumContext.NULL_UUID &&
            eventTimestamp != null
        ) {
            val newEntry = ViewEntry(activeViewId, eventTimestamp, hasReplay)
            addToCache(newEntry)
        }
        purgeHistory()
    }

    private fun addToCache(
        entry: ViewEntry
    ) {
        if (parentViewsHistoryQueue.isEmpty() ||
            (
            parentViewsHistoryQueue.first.viewId != entry.viewId &&
                    parentViewsHistoryQueue.first.timestamp <= entry.timestamp
            )
        ) {
            parentViewsHistoryQueue.addFirst(entry)
        } else if (parentViewsHistoryQueue.first.viewId == entry.viewId) {
            // the function is synchronized and we are checking the size before
            @Suppress("UnsafeThirdPartyFunctionCall")
            parentViewsHistoryQueue.removeFirst()
            parentViewsHistoryQueue.addFirst(entry)
        }
    }

    private fun purgeHistory() {
        var cursor = parentViewsHistoryQueue.peekLast()
        while (cursor != null) {
            val timeSinceLastSnapshot = System.currentTimeMillis() - cursor.timestamp
            if (timeSinceLastSnapshot > entriesTtlLimitInMs) {
                parentViewsHistoryQueue.remove(cursor)
                cursor = parentViewsHistoryQueue.peekLast()
            } else {
                break
            }
        }
        while (parentViewsHistoryQueue.size > DATA_CACHE_ENTRIES_LIMIT) {
            // the function is synchronized and we are checking the size before
            @Suppress("UnsafeThirdPartyFunctionCall")
            parentViewsHistoryQueue.removeLast()
        }
    }

    internal data class ViewEntry(
        val viewId: String,
        val timestamp: Long,
        val hasReplay: Boolean
    )

    companion object {
        internal const val VIEW_ID_KEY = "view_id"
        internal const val VIEW_TIMESTAMP_KEY = "view_timestamp"
        internal const val VIEW_HAS_REPLAY_KEY = "view_has_replay"
        internal val DATA_PURGE_TTL_LIMIT_IN_MS = TimeUnit.HOURS.toMillis(2)
        internal const val DATA_CACHE_ENTRIES_LIMIT = 30
    }
}
