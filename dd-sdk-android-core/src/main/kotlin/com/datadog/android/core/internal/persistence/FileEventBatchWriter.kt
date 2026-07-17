/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReaderWriter
import java.io.File
import java.util.Locale

internal class FileEventBatchWriter(
    private val fileOrchestrator: FileOrchestrator,
    private val eventsWriter: BatchFileReaderWriter,
    private val metadataReaderWriter: FileReaderWriter,
    private val filePersistenceConfig: FilePersistenceConfig,
    private val batchWriteEventListener: BatchWriteEventListener,
    private val internalLogger: InternalLogger
) : EventBatchWriter {

    @WorkerThread
    override fun write(
        event: RawBatchEvent,
        batchMetadata: ByteArray?,
        eventType: EventType
    ): Boolean {
        // prevent useless operation for empty event
        if (event.data.isEmpty()) {
            return true
        }
        if (!checkEventSize(event.data.size)) {
            return false
        }

        // Serialize once (TLV-wrapped, encrypted if configured) so the exact on-disk size is known
        // before asking the orchestrator for a file with enough room to hold it.
        val serializedEvent = eventsWriter.serializeToBytes(event) ?: return false

        val batchFile = fileOrchestrator.getWritableFile(serializedEvent.size.toLong())
        if (batchFile == null) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                { NO_BATCH_FILE_AVAILABLE }
            )
            return false
        }

        val metadataFile = fileOrchestrator.getMetadataFile(batchFile)

        return if (eventsWriter.writeBinaryData(batchFile, serializedEvent, true)) {
            batchWriteEventListener.onWriteEvent(event.data.size.toLong())
            if (batchMetadata?.isNotEmpty() == true && metadataFile != null) {
                writeBatchMetadata(metadataFile, batchMetadata)
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

    @WorkerThread
    private fun writeBatchMetadata(metadataFile: File, metadata: ByteArray) {
        val result = metadataReaderWriter.writeData(
            metadataFile,
            metadata,
            false
        )
        if (!result) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                {
                    WARNING_METADATA_WRITE_FAILED.format(
                        Locale.US,
                        metadataFile.path
                    )
                }
            )
        }
    }

    companion object {
        internal const val WARNING_METADATA_WRITE_FAILED = "Unable to write metadata file: %s"
        internal const val ERROR_LARGE_DATA = "Can't write data with size %d (max item size is %d)"
        internal const val NO_BATCH_FILE_AVAILABLE = "No batch file available"
    }
}
