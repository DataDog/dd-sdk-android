/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core.internal.storage

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.file.FileWriter
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.v2.api.EventBatchWriter
import com.datadog.android.v2.api.InternalLogger
import java.io.File
import java.util.Locale

internal class FileEventBatchWriter(
    private val batchFile: File,
    private val metadataFile: File?,
    private val eventsWriter: FileWriter,
    private val metadataReaderWriter: FileReaderWriter,
    private val filePersistenceConfig: FilePersistenceConfig,
    private val internalLogger: InternalLogger
) : EventBatchWriter {

    @WorkerThread
    override fun currentMetadata(): ByteArray? {
        if (metadataFile == null || !metadataFile.existsSafe()) return null

        return metadataReaderWriter.readData(metadataFile)
    }

    @WorkerThread
    override fun write(
        event: ByteArray,
        newMetadata: ByteArray?
    ): Boolean {
        // prevent useless operation for empty event
        return if (event.isEmpty()) {
            true
        } else if (!checkEventSize(event.size)) {
            false
        } else if (eventsWriter.writeData(batchFile, event, true)) {
            if (newMetadata?.isNotEmpty() == true && metadataFile != null) {
                writeBatchMetadata(metadataFile, newMetadata)
            }
            true
        } else {
            false
        }
    }

    private fun checkEventSize(eventSize: Int): Boolean {
        if (eventSize > filePersistenceConfig.maxItemSize) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                ERROR_LARGE_DATA.format(
                    Locale.US,
                    eventSize,
                    filePersistenceConfig.maxItemSize
                ),
                null,
                emptyMap()
            )
            return false
        }
        return true
    }

    @WorkerThread
    private fun writeBatchMetadata(metadataFile: File, metadata: ByteArray) {
        val result =
            metadataReaderWriter.writeData(metadataFile, metadata, false)
        if (!result) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                WARNING_METADATA_WRITE_FAILED.format(
                    Locale.US,
                    metadataFile.path
                ),
                null,
                emptyMap()
            )
        }
    }

    companion object {
        internal const val WARNING_METADATA_WRITE_FAILED = "Unable to write metadata file: %s"
        internal const val ERROR_LARGE_DATA = "Can't write data with size %d (max item size is %d)"
    }
}
