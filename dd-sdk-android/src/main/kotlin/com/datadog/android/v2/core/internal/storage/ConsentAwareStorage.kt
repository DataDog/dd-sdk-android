/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core.internal.storage

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReaderWriter
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.v2.api.BatchWriterListener
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.context.DatadogContext
import java.io.File
import java.util.Locale

internal class ConsentAwareStorage(
    private val grantedOrchestrator: FileOrchestrator,
    private val pendingOrchestrator: FileOrchestrator,
    private val batchEventsReaderWriter: BatchFileReaderWriter,
    private val batchMetadataReaderWriter: FileReaderWriter,
    private val fileMover: FileMover,
    private val listener: BatchWriterListener,
    private val internalLogger: InternalLogger,
    private val filePersistenceConfig: FilePersistenceConfig
) : Storage {

    /**
     * Keeps track of files currently being read.
     */
    private val lockedBatches: MutableSet<Batch> = mutableSetOf()

    /** @inheritdoc */
    @WorkerThread
    override fun writeCurrentBatch(
        datadogContext: DatadogContext,
        callback: (BatchWriter) -> Unit
    ) {
        val orchestrator = when (datadogContext.trackingConsent) {
            TrackingConsent.GRANTED -> grantedOrchestrator
            TrackingConsent.PENDING -> pendingOrchestrator
            TrackingConsent.NOT_GRANTED -> null
        }

        val batchFile = orchestrator?.getWritableFile()
        val metadataFile = if (batchFile != null) orchestrator.getMetadataFile(batchFile) else null

        val writer = object : BatchWriter {
            @WorkerThread
            override fun currentMetadata(): ByteArray? {
                if (orchestrator == null || metadataFile == null) return null

                return batchMetadataReaderWriter.readData(metadataFile)
            }

            @WorkerThread
            override fun write(event: ByteArray, eventId: String, newMetadata: ByteArray?) {
                // prevent useless operation for empty event / null orchestrator
                if (event.isEmpty() || orchestrator == null) {
                    listener.onDataWritten(eventId)
                    return
                }

                if (!checkEventSize(event.size)) {
                    listener.onDataWriteFailed(eventId)
                    return
                }

                if (batchFile != null &&
                    batchEventsReaderWriter.writeData(batchFile, event, true)
                ) {
                    if (newMetadata?.isNotEmpty() == true && metadataFile != null) {
                        writeBatchMetadata(metadataFile, newMetadata)
                    }
                    listener.onDataWritten(eventId)
                } else {
                    listener.onDataWriteFailed(eventId)
                }
            }
        }
        callback.invoke(writer)
    }

    /** @inheritdoc */
    @WorkerThread
    override fun readNextBatch(
        datadogContext: DatadogContext,
        callback: (BatchId, BatchReader) -> Unit
    ) {
        val (batchFile, metaFile) = synchronized(lockedBatches) {
            val batchFile = grantedOrchestrator
                .getReadableFile(lockedBatches.map { it.file }.toSet()) ?: return

            val metaFile = grantedOrchestrator.getMetadataFile(batchFile)
            lockedBatches.add(Batch(batchFile, metaFile))
            batchFile to metaFile
        }

        val batchId = BatchId.fromFile(batchFile)
        val reader = object : BatchReader {

            @WorkerThread
            override fun currentMetadata(): ByteArray? {
                if (metaFile == null || !metaFile.existsSafe()) return null

                return batchMetadataReaderWriter.readData(metaFile)
            }

            @WorkerThread
            override fun read(): List<ByteArray> {
                return batchEventsReaderWriter.readData(batchFile)
            }
        }
        callback(batchId, reader)
    }

    /** @inheritdoc */
    @WorkerThread
    override fun confirmBatchRead(batchId: BatchId, callback: (BatchConfirmation) -> Unit) {
        val batch = synchronized(lockedBatches) {
            lockedBatches.firstOrNull { batchId.matchesFile(it.file) }
        } ?: return
        val confirmation = object : BatchConfirmation {
            @WorkerThread
            override fun markAsRead(deleteBatch: Boolean) {
                if (deleteBatch) {
                    deleteBatchFile(batch.file)
                    if (batch.metaFile?.existsSafe() == true) {
                        deleteBatchMetadataFile(batch.metaFile)
                    }
                }
                synchronized(lockedBatches) {
                    lockedBatches.remove(batch)
                }
            }
        }
        callback(confirmation)
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
            batchMetadataReaderWriter.writeData(metadataFile, metadata, false)
        if (!result) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                WARNING_METADATA_WRITE_FAILED.format(Locale.US, metadataFile.path),
                null,
                emptyMap()
            )
        }
    }

    @WorkerThread
    private fun deleteBatchFile(batchFile: File) {
        val result = fileMover.delete(batchFile)
        if (!result) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                WARNING_DELETE_FAILED.format(Locale.US, batchFile.path),
                null,
                emptyMap()
            )
        }
    }

    @WorkerThread
    private fun deleteBatchMetadataFile(metadataFile: File) {
        val result = fileMover.delete(metadataFile)
        if (!result) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                WARNING_DELETE_FAILED.format(Locale.US, metadataFile.path),
                null,
                emptyMap()
            )
        }
    }

    private data class Batch(val file: File, val metaFile: File?)

    companion object {
        internal const val WARNING_DELETE_FAILED = "Unable to delete file: %s"
        internal const val ERROR_LARGE_DATA = "Can't write data with size %d (max item size is %d)"
        internal const val WARNING_METADATA_WRITE_FAILED = "Unable to write metadata file: %s"
    }
}
