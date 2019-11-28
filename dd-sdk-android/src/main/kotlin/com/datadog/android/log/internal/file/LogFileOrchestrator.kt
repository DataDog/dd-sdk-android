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
    private val writeAccessPerFile = mutableMapOf<String, Int>()

    // region FileOrchestrator

    override fun getWritableFile(itemSize: Int): File {
        val maxLogLength = maxBatchSize - itemSize
        val now = System.currentTimeMillis()

        val files = rootDirectory.listFiles(fileFilter).orEmpty().sorted()
        val lastFile = files.lastOrNull()

        val newFileName = now.toString()
        return if (lastFile != null) {
            val fileHasRoomForMore = lastFile.length() < maxLogLength
            val fileIsRecentEnough = LogFileStrategy.isFileRecent(lastFile, recentDelayMs)
            val fileLogCount = writeAccessPerFile[lastFile.name] ?: 0
            val fileHasSlotForMore = (fileLogCount > 0) && (fileLogCount < maxLogPerBatch)

            if (fileHasRoomForMore && fileIsRecentEnough && fileHasSlotForMore) {
                writeAccessPerFile[lastFile.name] = fileLogCount + 1
                lastFile
            } else {
                writeAccessPerFile[newFileName] = 1
                File(rootDirectory, newFileName)
            }
        } else {
            writeAccessPerFile[newFileName] = 1
            File(rootDirectory, newFileName)
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
}
