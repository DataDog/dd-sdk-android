/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.async

/**
 * References to the work queue context.
 */
internal data class RecordedDataQueueRefs(
    private val recordedDataQueueHandler: RecordedDataQueueHandler
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
        recordedDataQueueHandler.tryToConsumeItems()
    }
}
