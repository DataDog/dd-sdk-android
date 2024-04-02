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
    private val recordedDataQueueHandler: DataQueueHandler,
    private val applicationId: String
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
                resourceData = resourceData,
                applicationId = applicationId
            )
        }
    }
}
