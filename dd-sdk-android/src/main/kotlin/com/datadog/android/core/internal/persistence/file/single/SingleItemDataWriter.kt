/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.single

import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.persistence.Serializer
import com.datadog.android.core.internal.persistence.file.FileHandler
import com.datadog.android.core.internal.persistence.file.FileOrchestrator

internal open class SingleItemDataWriter<T : Any>(
    internal val fileOrchestrator: FileOrchestrator,
    internal val serializer: Serializer<T>,
    internal val handler: FileHandler
) : DataWriter<T> {

    // region DataWriter

    override fun write(element: T) {
        consume(element)
    }

    override fun write(data: List<T>) {
        consume(data.last())
    }

    // endregion

    // region Internal

    private fun consume(data: T) {
        val serialized = serializer.serialize(data) ?: return
        val byteArray = serialized.toByteArray(Charsets.UTF_8)

        synchronized(this) {
            writeData(byteArray)
        }
    }

    private fun writeData(byteArray: ByteArray): Boolean {
        val file = fileOrchestrator.getWritableFile(byteArray.size) ?: return false
        return handler.writeData(file, byteArray, false, null)
    }

    // endregion
}
