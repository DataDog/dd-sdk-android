/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.async

import com.datadog.android.sessionreplay.internal.processor.RumContextData
import com.datadog.android.sessionreplay.internal.recorder.Node
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import java.util.concurrent.atomic.AtomicInteger

internal class SnapshotRecordedDataQueueItem(
    rumContextData: RumContextData,
    internal val systemInformation: SystemInformation
) : RecordedDataQueueItem(rumContextData) {
    internal var nodes = emptyList<Node>()
    internal var pendingImages = AtomicInteger(0)

    override fun isValid(): Boolean {
        return nodes.isNotEmpty()
    }

    override fun isReady(): Boolean {
        return pendingImages.get() == 0
    }

    internal fun incrementPendingImages() = pendingImages.incrementAndGet()
    internal fun decrementPendingImages() = pendingImages.decrementAndGet()
}
