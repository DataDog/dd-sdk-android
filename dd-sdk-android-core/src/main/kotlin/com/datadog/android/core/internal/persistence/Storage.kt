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
     * Utility to read a batch, synchronously.
     */
    @WorkerThread
    fun readNextBatch(): BatchData?

    /**
     * Utility to update the state of a batch, synchronously.
     * @param batchId the id of the Batch to confirm
     * @param removalReason the reason why the batch is being removed
     * @param deleteBatch if `true` the batch will be deleted, otherwise it will be marked as
     * not readable.
     */
    @WorkerThread
    fun confirmBatchRead(
        batchId: BatchId,
        removalReason: RemovalReason,
        deleteBatch: Boolean
    )

    /**
     * Removes all the files backed by this storage, synchronously.
     */
    fun dropAll()
}
