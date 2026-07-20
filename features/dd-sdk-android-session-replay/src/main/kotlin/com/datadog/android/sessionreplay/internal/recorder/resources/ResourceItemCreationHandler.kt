/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import androidx.annotation.VisibleForTesting
import com.datadog.android.sessionreplay.internal.async.DataQueueHandler
import java.util.Collections

internal class ResourceItemCreationHandler(
    private val recordedDataQueueHandler: DataQueueHandler
) {
    // resource IDs previously sent in this session -
    // optimization to avoid sending the same resource multiple times
    // atm this set is unbounded but expected to use relatively little space (~80kb per 1k items)
    @VisibleForTesting internal val resourceIdsSeen: MutableSet<String> =
        Collections.synchronizedSet(HashSet<String>())

    internal fun queueItem(resourceId: String, resourceData: ByteArray) {
        if (resourceIdsSeen.contains(resourceId)) {
            return
        }

        // Deliberately kept in (not removed after investigation) at the user's explicit
        // request — see the git history/PR discussion for the "full screen broken images"
        // investigation this was added for. android.util.Log rather than InternalLogger:
        // this is meant to be read straight off Logcat on a real device/app while
        // reproducing the issue, not routed through the SDK's own telemetry/user-facing
        // channels. Confirms whether addResourceItem actually enqueued this resource
        // (non-null) or dropped it (null, e.g. an invalid RUM context — see
        // RumContextDataHandler's own logging for that case).
        val enqueued = recordedDataQueueHandler.addResourceItem(
            identifier = resourceId,
            resourceData = resourceData
        )
        android.util.Log.d(
            "DD_SessionReplay",
            "[ResourceItemCreationHandler] queueItem: resourceId=$resourceId enqueued=${enqueued != null}"
        )

        // Only mark resourceId as seen once it's actually been enqueued. Marking it
        // unconditionally (as this used to) meant a single dropped attempt — e.g. RUM context
        // still invalid on the very first snapshot cycle right after cold start, before RUM's
        // first view registers — permanently blacklisted this resourceId for the rest of the
        // process: every later capture of the exact same bitmap (a static icon reliably hashes
        // to the same resourceId) would find it already in resourceIdsSeen and skip re-queueing
        // it entirely, even once RUM context became valid. That silent, permanent drop is
        // consistent with "logcat shows onSuccess/applyResourceId for every icon, but the
        // replay viewer never renders any of them" — this was investigated and confirmed as
        // the root cause of that exact symptom.
        if (enqueued != null) {
            resourceIdsSeen.add(resourceId)
        }
    }
}
