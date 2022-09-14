/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core.internal.storage

import androidx.annotation.WorkerThread
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.tools.annotation.NoOpImplementation

/**
 * Main core api to interact with the local storage of events.
 */
@NoOpImplementation
internal interface Storage {

    /**
     * Utility to write data, asynchronously.
     * @param callback an operation to perform with a [BatchWriter] that will target the current
     * writeable Batch
     */
    @WorkerThread
    fun writeCurrentBatch(datadogContext: DatadogContext, callback: (BatchWriter) -> Unit)

    /**
     * Utility to read a batch, asynchronously.
     * @param noBatchCallback an optional callback which is called when there is no batch available to read.
     * @param batchCallback an operation to perform with a [BatchId] and [BatchReader] that will target
     * the next readable Batch
     */
    @WorkerThread
    fun readNextBatch(
        noBatchCallback: () -> Unit = {},
        batchCallback: (BatchId, BatchReader) -> Unit
    )

    /**
     * Utility to update the state of a batch, asynchronously.
     * @param batchId the id of the Batch to confirm
     * @param callback an operation to perform with a [BatchConfirmation]
     */
    @WorkerThread
    fun confirmBatchRead(batchId: BatchId, callback: (BatchConfirmation) -> Unit)
}
