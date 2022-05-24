/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core.internal.storage

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.persistence.file.FileHandler
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.v2.api.BatchWriterListener
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.context.DatadogContext
import java.io.File
import java.util.Locale

internal class ConsentAwareStorage(
    private val grantedOrchestrator: FileOrchestrator,
    private val pendingOrchestrator: FileOrchestrator,
    private val handler: FileHandler,
    private val listener: BatchWriterListener,
    private val internalLogger: InternalLogger
) : Storage {

    /**
     * Keeps track of files currently being read.
     */
    private val lockedFiles: MutableSet<File> = mutableSetOf()

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

        val writer = object : BatchWriter {
            @WorkerThread
            override fun currentMetadata(): ByteArray? {
                // TODO RUMM-2186 handle writing/updating batch metadata in separate file
                return null
            }

            @WorkerThread
            override fun write(event: ByteArray, eventId: String, newMetadata: ByteArray) {
                // prevent useless operation for empty event / null orchestrator
                if (event.isEmpty() || orchestrator == null) {
                    listener.onDataWritten(eventId)
                    return
                }

                val file = orchestrator.getWritableFile(event.size)

                if (file != null && handler.writeData(file, event, true)) {
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
        val batchFile = synchronized(lockedFiles) {
            grantedOrchestrator.getReadableFile(lockedFiles)?.also {
                lockedFiles.add(it)
            } ?: return
        }

        val batchId = BatchId.fromFile(batchFile)
        val reader = object : BatchReader {
            @WorkerThread
            override fun read(batchId: BatchId): List<ByteArray> {
                // TODO RUMM-2186 check the batch id match this reader
                return handler.readData(batchFile)
            }
        }
        callback(batchId, reader)
    }

    /** @inheritdoc */
    @WorkerThread
    override fun confirmBatchRead(batchId: BatchId, callback: (BatchConfirmation) -> Unit) {
        val batchFile = synchronized(lockedFiles) {
            lockedFiles.firstOrNull { batchId.matchesFile(it) }
        } ?: return
        val confirmation = object : BatchConfirmation {
            @WorkerThread
            override fun markAsRead(deleteBatch: Boolean) {
                if (deleteBatch) {
                    val result = handler.delete(batchFile)
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
                synchronized(lockedFiles) {
                    lockedFiles.remove(batchFile)
                }
            }
        }
        callback(confirmation)
    }

    companion object {
        internal const val WARNING_DELETE_FAILED = "Unable to delete file: %s"
    }
}
