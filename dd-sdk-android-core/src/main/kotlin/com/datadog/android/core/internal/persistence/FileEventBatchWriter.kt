/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.generated.DdSdkAndroidCoreLogger
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.file.FileWriter
import com.datadog.android.core.internal.persistence.file.existsSafe
import java.io.File

internal class FileEventBatchWriter(
    private val fileOrchestrator: FileOrchestrator,
    private val eventsWriter: FileWriter<RawBatchEvent>,
    private val metadataReaderWriter: FileReaderWriter,
    private val filePersistenceConfig: FilePersistenceConfig,
    private val batchWriteEventListener: BatchWriteEventListener,
    private val internalLogger: InternalLogger
) : EventBatchWriter {

    private val logger = DdSdkAndroidCoreLogger(internalLogger)

    @get:WorkerThread
    private val batchFile: File? by lazy {
        @Suppress("ThreadSafety") // called in the worker context
        fileOrchestrator.getWritableFile()
    }

    @get:WorkerThread
    private val metadataFile: File?
        get() = batchFile?.let {
            @Suppress("ThreadSafety") // called in the worker context
            fileOrchestrator.getMetadataFile(it)
        }

    @WorkerThread
    override fun currentMetadata(): ByteArray? {
        return with(metadataFile) {
            if (this == null || !existsSafe(internalLogger)) {
                null
            } else {
                metadataReaderWriter.readData(this)
            }
        }
    }

    @WorkerThread
    override fun write(
        event: RawBatchEvent,
        batchMetadata: ByteArray?,
        eventType: EventType
    ): Boolean {
        val (batchFile, metadataFile) = batchFile to metadataFile
        if (batchFile == null) {
            logger.logNoBatchFileAvailable()
            return false
        }

        // prevent useless operation for empty event
        return if (event.data.isEmpty()) {
            true
        } else if (!checkEventSize(event.data.size)) {
            false
        } else if (eventsWriter.writeData(batchFile, event, true)) {
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
            logger.logBatchErrorLargeData(
                eventSize = eventSize,
                maxItemSize = filePersistenceConfig.maxItemSize.toInt()
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
            logger.logWarningMetadataWriteFailed(path = metadataFile.path)
        }
    }
}
