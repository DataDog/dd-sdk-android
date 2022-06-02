/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.persistence.PayloadDecoration
import com.datadog.android.core.internal.persistence.Serializer
import com.datadog.android.core.internal.persistence.file.ChunkedFileHandler
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.serializeToByteArray
import com.datadog.android.log.Logger
import java.util.Locale

/**
 * A [DataWriter] storing data in batch files.
 */
internal open class BatchFileDataWriter<T : Any>(
    internal val fileOrchestrator: FileOrchestrator,
    internal val serializer: Serializer<T>,
    internal val decoration: PayloadDecoration,
    internal val handler: ChunkedFileHandler,
    internal val internalLogger: Logger,
    // TODO RUMM-0000 don't use default value
    internal val filePersistenceConfig: FilePersistenceConfig = FilePersistenceConfig()
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
            val success = writeData(byteArray)
            if (success) {
                onDataWritten(data, byteArray)
            } else {
                onDataWriteFailed(data)
            }
        }
    }

    @WorkerThread
    private fun writeData(byteArray: ByteArray): Boolean {
        if (!checkEventSize(byteArray.size)) return false

        val file = fileOrchestrator.getWritableFile() ?: return false
        return handler.writeData(file, byteArray, true)
    }
    private fun checkEventSize(eventSize: Int): Boolean {
        if (eventSize > filePersistenceConfig.maxItemSize) {
            internalLogger.e(
                ERROR_LARGE_DATA.format(
                    Locale.US,
                    eventSize,
                    filePersistenceConfig.maxItemSize
                )
            )
            return false
        }
        return true
    }

    // endregion

    companion object {
        internal const val ERROR_LARGE_DATA = "Can't write data with size %d (max item size is %d)"
    }
}
