/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.persistence.PayloadDecoration
import com.datadog.android.core.internal.persistence.Serializer
import com.datadog.android.core.internal.persistence.file.FileHandler
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.serializeToByteArray
import com.datadog.android.log.Logger

/**
 * A [DataWriter] storing data in batch files.
 */
internal open class BatchFileDataWriter<T : Any>(
    internal val fileOrchestrator: FileOrchestrator,
    internal val serializer: Serializer<T>,
    internal val decoration: PayloadDecoration,
    internal val handler: FileHandler,
    internal val internalLogger: Logger
) : DataWriter<T> {

    // region DataWriter

    override fun write(element: T) {
        consume(element)
    }

    override fun write(data: List<T>) {
        data.forEach { consume(it) }
    }

    // endregion

    // region Protected

    /**
     * Called whenever data is written successfully.
     * @param data the data written
     */
    internal open fun onDataWritten(data: T, rawData: ByteArray) {}

    /**
     * Called whenever data failed to be written.
     * @param data the data
     */
    internal open fun onDataWriteFailed(data: T) {}

    // endregion

    // region Internal

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

    private fun writeData(byteArray: ByteArray): Boolean {
        val file = fileOrchestrator.getWritableFile(byteArray.size) ?: return false
        return handler.writeData(file, byteArray, true, decoration.separatorBytes)
    }

    // endregion
}
