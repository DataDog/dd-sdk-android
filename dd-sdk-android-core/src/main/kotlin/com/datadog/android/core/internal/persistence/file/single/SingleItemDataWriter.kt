/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.single

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.FileWriter
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.core.persistence.serializeToByteArray
import com.datadog.android.v2.api.InternalLogger
import java.util.Locale

internal open class SingleItemDataWriter<T : Any>(
    internal val fileOrchestrator: FileOrchestrator,
    internal val serializer: Serializer<T>,
    internal val fileWriter: FileWriter,
    internal val internalLogger: InternalLogger,
    internal val filePersistenceConfig: FilePersistenceConfig
) : DataWriter<T> {

    // region DataWriter

    @WorkerThread
    override fun write(element: T) {
        consume(element)
    }

    @WorkerThread
    override fun write(data: List<T>) {
        val element = data.lastOrNull() ?: return
        consume(element)
    }

    // endregion

    // region Internal

    @WorkerThread
    private fun consume(data: T) {
        val byteArray = serializer.serializeToByteArray(data, internalLogger) ?: return

        synchronized(this) {
            writeData(byteArray)
        }
    }

    @Suppress("ReturnCount")
    @WorkerThread
    private fun writeData(byteArray: ByteArray): Boolean {
        if (!checkEventSize(byteArray.size)) return false
        val file = fileOrchestrator.getWritableFile() ?: return false
        return fileWriter.writeData(file, byteArray, false)
    }

    private fun checkEventSize(eventSize: Int): Boolean {
        if (eventSize > filePersistenceConfig.maxItemSize) {
            // DISCUSS? send a RUM/Log Error event here to the org so they get visibility
            // about this in their own org?
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                {
                    ERROR_LARGE_DATA.format(
                        Locale.US,
                        eventSize,
                        filePersistenceConfig.maxItemSize
                    )
                }
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
