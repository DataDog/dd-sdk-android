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
import com.datadog.android.core.internal.utils.submitSafe
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.v2.api.EventBatchWriter
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.context.DatadogContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService

internal class ConsentAwareStorage(
    private val executorService: ExecutorService,
    private val grantedOrchestrator: FileOrchestrator,
    private val pendingOrchestrator: FileOrchestrator,
    private val batchEventsReaderWriter: BatchFileReaderWriter,
    private val batchMetadataReaderWriter: FileReaderWriter,
    private val fileMover: FileMover,
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
        forceNewBatch: Boolean,
        callback: (EventBatchWriter) -> Unit
    ) {
        val orchestrator = when (datadogContext.trackingConsent) {
            TrackingConsent.GRANTED -> grantedOrchestrator
            TrackingConsent.PENDING -> pendingOrchestrator
            TrackingConsent.NOT_GRANTED -> null
        }

        executorService.submitSafe("Data write", internalLogger) {
            val batchFile = orchestrator?.getWritableFile(forceNewBatch)
            val metadataFile = if (batchFile != null) {
                orchestrator.getMetadataFile(batchFile)
            } else {
                null
            }
            val writer = if (orchestrator == null || batchFile == null) {
                NoOpEventBatchWriter()
            } else {
                FileEventBatchWriter(
                    batchFile = batchFile,
                    metadataFile = metadataFile,
                    eventsWriter = batchEventsReaderWriter,
                    metadataReaderWriter = batchMetadataReaderWriter,
                    filePersistenceConfig = filePersistenceConfig,
                    internalLogger = internalLogger
                )
            }
            callback.invoke(writer)
        }
    }

    /** @inheritdoc */
    @WorkerThread
    override fun readNextBatch(
        noBatchCallback: () -> Unit,
        batchCallback: (BatchId, BatchReader) -> Unit
    ) {
        val (batchFile, metaFile) = synchronized(lockedBatches) {
            val batchFile = grantedOrchestrator
                .getReadableFile(lockedBatches.map { it.file }.toSet())
            if (batchFile == null) {
                noBatchCallback()
                return
            }

            val metaFile = grantedOrchestrator.getMetadataFile(batchFile)
            lockedBatches.add(Batch(batchFile, metaFile))
            batchFile to metaFile
        }

        val batchId = BatchId.fromFile(batchFile)
        val reader = object : BatchReader {

            @WorkerThread
            override fun currentMetadata(): ByteArray? {
                if (metaFile == null || !metaFile.existsSafe(internalLogger)) return null

                return batchMetadataReaderWriter.readData(metaFile)
            }

            @WorkerThread
            override fun read(): List<ByteArray> {
                return batchEventsReaderWriter.readData(batchFile)
            }
        }
        batchCallback(batchId, reader)
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
                    deleteBatch(batch)
                }
                synchronized(lockedBatches) {
                    lockedBatches.remove(batch)
                }
            }
        }
        callback(confirmation)
    }

    /** @inheritdoc */
    @WorkerThread
    override fun dropAll() {
        synchronized(lockedBatches) {
            lockedBatches.forEach {
                deleteBatch(it)
                lockedBatches.remove(it)
            }
        }

        arrayOf(pendingOrchestrator, grantedOrchestrator).forEach { orchestrator ->
            orchestrator.getAllFiles().forEach {
                val metaFile = orchestrator.getMetadataFile(it)
                deleteBatch(it, metaFile)
            }
        }
    }

    @WorkerThread
    private fun deleteBatch(batch: Batch) {
        deleteBatch(batch.file, batch.metaFile)
    }

    @WorkerThread
    private fun deleteBatch(batchFile: File, metaFile: File?) {
        deleteBatchFile(batchFile)
        if (metaFile?.existsSafe(internalLogger) == true) {
            deleteBatchMetadataFile(metaFile)
        }
    }

    @WorkerThread
    private fun deleteBatchFile(batchFile: File) {
        val result = fileMover.delete(batchFile)
        if (!result) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                WARNING_DELETE_FAILED.format(Locale.US, batchFile.path)
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
                WARNING_DELETE_FAILED.format(Locale.US, metadataFile.path)
            )
        }
    }

    private data class Batch(val file: File, val metaFile: File?)

    companion object {
        internal const val WARNING_DELETE_FAILED = "Unable to delete file: %s"
    }
}
