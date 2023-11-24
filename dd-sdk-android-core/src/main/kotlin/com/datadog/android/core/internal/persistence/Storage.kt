/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.core.internal.metrics.RemovalReason
import com.datadog.tools.annotation.NoOpImplementation

/**
 * Main core api to interact with the local storage of events.
 */
@NoOpImplementation
internal interface Storage {

    // TODO RUMM-0000 It seems only write part is useful to be async. Having upload part as async
    //  brings more complexity and is not really needed, because there is a dedicated upload
    //  thread anyway and upload part is not exposed as public API (except only data ->
    //   request transformation).
    /**
     * Utility to write data, asynchronously.
     * @param datadogContext the context for the write operation
     * @param forceNewBatch if `true` will force the writer to start a new batch before storing the
     * data. By default this flag is `false`.
     * @param callback an operation to perform with a [EventBatchWriter] that will target the current
     * writeable Batch
     */
    @AnyThread
    fun writeCurrentBatch(
        datadogContext: DatadogContext,
        forceNewBatch: Boolean,
        callback: (EventBatchWriter) -> Unit
    )

    /**
     * Utility to read a batch, asynchronously.
     * @param noBatchCallback an optional callback which is called when there is no batch available to read.
     * @param readBatchCallback an operation to perform with a [BatchId] and [BatchReader] that will target
     * the next readable Batch
     */
    @WorkerThread
    fun readNextBatch(
        noBatchCallback: () -> Unit = {},
        readBatchCallback: (BatchId, BatchReader) -> Unit
    )

    /**
     * Utility to update the state of a batch, asynchronously.
     * @param batchId the id of the Batch to confirm
     * @param removalReason the reason why the batch is being removed
     * @param callback an operation to perform with a [BatchConfirmation]
     */
    @WorkerThread
    fun confirmBatchRead(
        batchId: BatchId,
        removalReason: RemovalReason,
        callback: (BatchConfirmation) -> Unit
    )

    /**
     * Removes all the files backed by this storage, synchronously.
     */
    fun dropAll()
}
