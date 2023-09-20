/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.async

import android.os.Handler
import android.os.Looper

/**
 * References to the work queue context.
 */
internal data class RecordedDataQueueRefs(
    private val recordedDataQueueHandler: RecordedDataQueueHandler,
    private val mainThreadHandler: Handler = Handler(Looper.getMainLooper())
) {
    // this can only be populated after the snapshot has been created
    internal var recordedDataQueueItem: SnapshotRecordedDataQueueItem? = null

    internal fun incrementPendingJobs() {
        recordedDataQueueItem?.incrementPendingJobs()
    }

    internal fun decrementPendingJobs() {
        recordedDataQueueItem?.decrementPendingJobs()
    }

    internal fun tryToConsumeItem() {
        mainThreadHandler.post {
            @Suppress("ThreadSafety") // we are in the main thread context
            recordedDataQueueHandler.tryToConsumeItems()
        }
    }
}
