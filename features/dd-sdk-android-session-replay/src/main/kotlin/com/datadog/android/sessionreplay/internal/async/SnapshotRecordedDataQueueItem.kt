/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.async

import com.datadog.android.sessionreplay.internal.processor.RecordedQueuedItemContext
import com.datadog.android.sessionreplay.internal.recorder.Node
import com.datadog.android.sessionreplay.recorder.SystemInformation
import java.util.concurrent.atomic.AtomicInteger

internal class SnapshotRecordedDataQueueItem(
    recordedQueuedItemContext: RecordedQueuedItemContext,
    internal val systemInformation: SystemInformation,
    creationTimestampInNs: Long
) : RecordedDataQueueItem(recordedQueuedItemContext, creationTimestampInNs) {
    @Volatile internal var nodes = emptyList<Node>()

    @Volatile internal var isFinishedTraversal = false
    internal val pendingJobs = AtomicInteger(0)

    override fun isValid(): Boolean {
        if (!isFinishedTraversal) {
            // item is always valid unless traversal has finished
            return true
        }

        return nodes.isNotEmpty()
    }

    override fun isReady(): Boolean {
        return isFinishedTraversal && pendingJobs.get() == 0
    }

    internal fun incrementPendingJobs() = pendingJobs.incrementAndGet()
    internal fun decrementPendingJobs() = pendingJobs.decrementAndGet()
}
