/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.persistence.Serializer
import com.datadog.android.core.internal.persistence.serializeToByteArray
import com.datadog.android.log.Logger
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.android.v2.core.internal.storage.Storage

/**
 * A [DataWriter] storing data in batch files.
 */
internal open class BatchFileDataWriter<T : Any>(
    internal val storage: Storage,
    internal val contextProvider: ContextProvider,
    internal val serializer: Serializer<T>,
    internal val internalLogger: Logger
) : DataWriter<T> {

    // region DataWriter

    @WorkerThread
    override fun write(element: T) {
        consume(element)
    }

    @WorkerThread
    override fun write(data: List<T>) {
        data.forEach { consume(it) }
    }

    // endregion

    // region Protected

    /**
     * Called whenever data is written successfully.
     * @param data the data written
     * @param rawData the data written (as the actual [ByteArray] written on disk)
     */
    @WorkerThread
    internal open fun onDataWritten(data: T, rawData: ByteArray) {}

    /**
     * Called whenever data failed to be written.
     * @param data the data
     */
    @WorkerThread
    internal open fun onDataWriteFailed(data: T) {}

    // endregion

    // region Internal

    @WorkerThread
    private fun consume(data: T) {
        val byteArray = serializer.serializeToByteArray(data, internalLogger) ?: return

        synchronized(this) {
            val context = contextProvider.context
            storage.writeCurrentBatch(context) {
                val result = it.write(byteArray, null)
                if (result) {
                    onDataWritten(data, byteArray)
                } else {
                    onDataWriteFailed(data)
                }
            }
        }
    }

    // endregion
}
