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
    private val maxBatchSize: Long
) : FileOrchestrator {

    private val fileFilter: FileFilter = LogFileFilter()

    // region FileOrchestrator

    override fun getWritableFile(itemSize: Int): File {
        val maxLogLength = maxBatchSize - itemSize
        val now = System.currentTimeMillis()

        val files = rootDirectory.listFiles(fileFilter).orEmpty().sorted()
        val lastFile = files.lastOrNull()

        return if (lastFile != null) {
            val fileHasRoomForMore = lastFile.length() < maxLogLength
            val fileIsRecentEnough = LogFileStrategy.isFileRecent(lastFile, recentDelayMs)

            if (fileHasRoomForMore && fileIsRecentEnough) {
                lastFile
            } else {
                File(rootDirectory, now.toString())
            }
        } else {
            File(rootDirectory, now.toString())
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
