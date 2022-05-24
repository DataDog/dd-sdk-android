/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.single

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.persistence.Serializer
import com.datadog.android.core.internal.persistence.file.FileHandler
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.serializeToByteArray
import com.datadog.android.log.Logger

internal open class SingleItemDataWriter<T : Any>(
    internal val fileOrchestrator: FileOrchestrator,
    internal val serializer: Serializer<T>,
    internal val handler: FileHandler,
    internal val internalLogger: Logger
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

    @WorkerThread
    private fun writeData(byteArray: ByteArray): Boolean {
        val file = fileOrchestrator.getWritableFile(byteArray.size) ?: return false
        return handler.writeData(file, byteArray, false)
    }

    // endregion
}
