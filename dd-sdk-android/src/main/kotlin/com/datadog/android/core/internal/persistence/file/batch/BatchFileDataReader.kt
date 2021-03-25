/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import com.datadog.android.core.internal.persistence.Batch
import com.datadog.android.core.internal.persistence.DataReader
import com.datadog.android.core.internal.persistence.PayloadDecoration
import com.datadog.android.core.internal.persistence.file.FileHandler
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.log.Logger
import java.io.File
import java.util.Locale

/**
 * A [DataReader] reading [Batch] data from files.
 */
internal class BatchFileDataReader(
    internal val fileOrchestrator: FileOrchestrator,
    internal val decoration: PayloadDecoration,
    internal val handler: FileHandler,
    internal val internalLogger: Logger
) : DataReader {

    private val lockedFiles: MutableList<File> = mutableListOf()

    // region DataReader

    override fun lockAndReadNext(): Batch? {
        val file = getAndLockReadableFile() ?: return null
        val data = handler.readData(file, decoration.prefixBytes, decoration.suffixBytes)

        return Batch(file.name, data)
    }

    override fun release(data: Batch) {
        releaseFile(data.id, delete = false)
    }

    override fun drop(data: Batch) {
        releaseFile(data.id, delete = true)
    }

    override fun dropAll() {
        synchronized(lockedFiles) {
            lockedFiles.toTypedArray().forEach {
                releaseFile(it, delete = true)
            }
        }

        fileOrchestrator.getAllFiles().forEach {
            deleteFile(it)
        }
    }

    // endregion

    // region Internal

    private fun getAndLockReadableFile(): File? {
        synchronized(lockedFiles) {
            val readableFile = fileOrchestrator.getReadableFile(lockedFiles.toSet())
            if (readableFile != null) {
                lockedFiles.add(readableFile)
            }
            return readableFile
        }
    }

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
            internalLogger.w(
                WARNING_UNKNOWN_BATCH_ID.format(Locale.US, fileName)
            )
        }
    }

    private fun releaseFile(
        file: File,
        delete: Boolean
    ) {
        if (delete) deleteFile(file)
        synchronized(lockedFiles) {
            lockedFiles.remove(file)
        }
    }

    private fun deleteFile(file: File) {
        if (!handler.delete(file)) {
            internalLogger.w(
                WARNING_DELETE_FAILED.format(Locale.US, file.path)
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
