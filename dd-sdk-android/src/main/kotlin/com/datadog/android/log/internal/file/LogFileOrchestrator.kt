/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.file

import java.io.File
import java.io.FileFilter

internal class LogFileOrchestrator(
    private val rootDirectory: File,
    private val recentDelayMs: Long,
    private val maxBatchSize: Long,
    private val maxLogPerBatch: Int
) : FileOrchestrator {

    private val fileFilter: FileFilter = LogFileFilter()

    private var previousFile: File? = null
    private var previousFileLogCount: Int = 0

    // region FileOrchestrator

    override fun getWritableFile(itemSize: Int): File {

        val files = rootDirectory.listFiles(fileFilter).orEmpty().sorted()
        val lastFile = files.lastOrNull()
        val lastKnownFile = previousFile
        val lastKnownFileCount = previousFileLogCount

        return if (lastFile != null && lastKnownFile == lastFile) {
            val newSize = lastFile.length() + itemSize
            val fileHasRoomForMore = newSize < maxBatchSize
            val fileIsRecentEnough = LogFileStrategy.isFileRecent(lastFile, recentDelayMs)
            val fileHasSlotForMore = (lastKnownFileCount < maxLogPerBatch)

            if (fileHasRoomForMore && fileIsRecentEnough && fileHasSlotForMore) {
                previousFileLogCount = lastKnownFileCount + 1
                lastFile
            } else {
                newLogFile()
            }
        } else {
            newLogFile()
        }
    }

    override fun getReadableFile(excludeFileNames: Set<String>): File? {
        val files = rootDirectory.listFiles(fileFilter).orEmpty().sorted()
        val nextLogFile = files.firstOrNull { it.name !in excludeFileNames }
        return if (nextLogFile == null) {
            null
        } else {
            if (LogFileStrategy.isFileRecent(nextLogFile, recentDelayMs)) {
                null
            } else {
                nextLogFile
            }
        }
    }

    // endregion

    // region Internal

    private fun newLogFile(): File {
        val newFileName = System.currentTimeMillis().toString()
        val newFile = File(rootDirectory, newFileName)
        previousFile = newFile
        previousFileLogCount = 1
        return newFile
    }

    // endregion
}
