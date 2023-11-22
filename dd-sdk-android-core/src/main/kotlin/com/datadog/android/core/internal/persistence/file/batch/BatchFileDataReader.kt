/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.Batch
import com.datadog.android.core.internal.persistence.DataReader
import com.datadog.android.core.internal.persistence.PayloadDecoration
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.utils.join
import java.io.File
import java.util.Locale

/**
 * A [DataReader] reading [Batch] data from files.
 */
internal class BatchFileDataReader(
    internal val fileOrchestrator: FileOrchestrator,
    internal val decoration: PayloadDecoration,
    internal val fileReader: BatchFileReader,
    internal val fileMover: FileMover,
    internal val internalLogger: InternalLogger
) : DataReader {

    private val lockedFiles: MutableList<File> = mutableListOf()

    // region DataReader

    @WorkerThread
    override fun lockAndReadNext(): Batch? {
        val file = getAndLockReadableFile() ?: return null
        val data = fileReader.readData(file)
            .map { it.data }
            .join(
                separator = decoration.separatorBytes,
                prefix = decoration.prefixBytes,
                suffix = decoration.suffixBytes,
                internalLogger
            )

        return Batch(file.name, data)
    }

    @WorkerThread
    override fun release(data: Batch) {
        releaseFile(data.id, delete = false)
    }

    @WorkerThread
    override fun drop(data: Batch) {
        releaseFile(data.id, delete = true)
    }

    @WorkerThread
    override fun dropAll() {
        synchronized(lockedFiles) {
            lockedFiles.toTypedArray().forEach {
                releaseFile(it, delete = true)
            }
        }

        fileOrchestrator.getAllFiles().forEach {
            val metaFile = fileOrchestrator.getMetadataFile(it)
            deleteFile(it)
            if (metaFile?.existsSafe(internalLogger) == true) {
                deleteFile(metaFile)
            }
        }
    }

    // endregion

    // region Internal

    @WorkerThread
    private fun getAndLockReadableFile(): File? {
        synchronized(lockedFiles) {
            val readableFile = fileOrchestrator.getReadableFile(lockedFiles.toSet())
            if (readableFile != null) {
                lockedFiles.add(readableFile)
            }
            return readableFile
        }
    }

    @WorkerThread
    private fun releaseFile(
        fileName: String,
        delete: Boolean
    ) {
        val file = synchronized(lockedFiles) {
            lockedFiles.firstOrNull { it.name == fileName }
        }
        if (file != null) {
            releaseFile(file, delete)
        } else {
            internalLogger.log(
                level = InternalLogger.Level.WARN,
                target = InternalLogger.Target.MAINTAINER,
                { WARNING_UNKNOWN_BATCH_ID.format(Locale.US, fileName) }
            )
        }
    }

    @WorkerThread
    private fun releaseFile(
        file: File,
        delete: Boolean
    ) {
        if (delete) {
            val metaFile = fileOrchestrator.getMetadataFile(file)
            deleteFile(file)
            if (metaFile?.existsSafe(internalLogger) == true) {
                deleteFile(metaFile)
            }
        }
        synchronized(lockedFiles) {
            lockedFiles.remove(file)
        }
    }

    @WorkerThread
    private fun deleteFile(file: File) {
        if (!fileMover.delete(file)) {
            internalLogger.log(
                level = InternalLogger.Level.WARN,
                target = InternalLogger.Target.MAINTAINER,
                { WARNING_DELETE_FAILED.format(Locale.US, file.path) }
            )
        }
    }

    // endregion

    internal companion object {
        internal const val WARNING_UNKNOWN_BATCH_ID =
            "Attempting to unlock or delete an unknown file: %s"
        internal const val WARNING_DELETE_FAILED =
            "Unable to delete file: %s"
    }
}
