/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import androidx.annotation.VisibleForTesting
import com.datadog.android.sessionreplay.internal.async.DataQueueHandler
import java.util.Collections

/**
 * [eagerDrain] should be `true` only for a caller whose own trigger for
 * [DataQueueHandler.tryToConsumeItems] isn't tied to the device's actual draw cadence - the
 * composition pipeline only drains this queue as a side effect of a full capture generation
 * completing, which (unlike the legacy pipeline's per-`View.onDraw()` drain) can legitimately sit
 * idle for longer than this queue's own expiry window, e.g. on a screen that renders once and then
 * sits still - so this item would otherwise expire, unconsumed, before the next generation ever
 * completes. Legacy leaves this `false`: its own drain cadence already makes the same race
 * negligible, and eagerly draining there would just add redundant executor churn to an
 * already-frequent call.
 */
internal class ResourceItemCreationHandler(
    private val recordedDataQueueHandler: DataQueueHandler,
    private val eagerDrain: Boolean = false
) {
    // resource IDs previously sent in this session -
    // optimization to avoid sending the same resource multiple times
    // atm this set is unbounded but expected to use relatively little space (~80kb per 1k items)
    @VisibleForTesting internal val resourceIdsSeen: MutableSet<String> =
        Collections.synchronizedSet(HashSet<String>())

    internal fun queueItem(resourceId: String, resourceData: ByteArray) {
        if (!resourceIdsSeen.contains(resourceId)) {
            resourceIdsSeen.add(resourceId)

            recordedDataQueueHandler.addResourceItem(
                identifier = resourceId,
                resourceData = resourceData
            )
            if (eagerDrain) {
                recordedDataQueueHandler.tryToConsumeItems()
            }
        }
    }
}
