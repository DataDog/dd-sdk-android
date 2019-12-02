/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.file

import java.io.File
import java.io.FileFilter
import java.util.concurrent.TimeUnit

internal class LogFileOrchestrator(
    private val rootDirectory: File,
    recentDelayMs: Long,
    private val maxBatchSize: Long,
    private val maxLogPerBatch: Int,
    private val oldFileThreshold: Long
) : FileOrchestrator {

    private val fileFilter: FileFilter = LogFileFilter()

    private var previousFile: File? = null
    private var previousFileLogCount: Int = 0

    // Offset the recent threshold for read and write to avoid conflicts
    // Arbitrary offset as 5% of the threshold
    private val recentWriteDelayMs = recentDelayMs - (recentDelayMs / 20)
    private val recentReadDelayMs = recentDelayMs + (recentDelayMs / 20)

    // region FileOrchestrator

    override fun getWritableFile(itemSize: Int): File {

        val files = rootDirectory.listFiles(fileFilter).orEmpty().sorted()
        val lastFile = files.lastOrNull()
        val lastKnownFile = previousFile
        val lastKnownFileCount = previousFileLogCount

        // regarding the (lastKnownFile == lastFile) check, it can fail for 3 reasons :
        //  1. the last file was written during a previous session (lastKnownFile == null)
        //  2. something else created a more recent file in the folder
        //  3. the lastKnownFile was deleted from the system
        // In any case, we don't know the log count, so to be safe, we create a new log file.
        return if (lastFile != null && lastKnownFile == lastFile) {
            val newSize = lastFile.length() + itemSize
            val fileHasRoomForMore = newSize < maxBatchSize
            val fileIsRecentEnough = isFileRecent(lastFile, recentWriteDelayMs)
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
        val threshold = System.currentTimeMillis() - oldFileThreshold

        // delete really old files !
        files
            .asSequence()
            .filter { it.name.toLong() < threshold }
            .forEach { it.delete() }

        val nextLogFile = files.firstOrNull {
            (it.name !in excludeFileNames) && (it.name.toLong() >= threshold)
        }
        return if (nextLogFile == null) {
            null
        } else {
            if (isFileRecent(nextLogFile, recentReadDelayMs)) {
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

    private fun isFileRecent(file: File, recentDelayMs: Long): Boolean {
        val now = System.currentTimeMillis()
        val fileTimestamp = file.name.toLong()
        return fileTimestamp >= (now - recentDelayMs)
    }

    // endregion

    companion object {
        private var ONE_SECOND_MS = TimeUnit.SECONDS.toMillis(1)
    }
}
