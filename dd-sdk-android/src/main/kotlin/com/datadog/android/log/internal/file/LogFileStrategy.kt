/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.file

import android.content.Context
import com.datadog.android.core.internal.data.Reader
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.data.file.FileOrchestrator
import com.datadog.android.core.internal.data.file.FileReader
import com.datadog.android.core.internal.data.file.FileWriter
import com.datadog.android.log.internal.LogStrategy
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.domain.LogSerializer
import java.io.File

internal class LogFileStrategy(
    private val rootDir: File,
    recentDelayMs: Long,
    maxBatchSize: Long,
    maxLogPerBatch: Int,
    oldFileThreshold: Long,
    maxDiskSpace: Long
) : LogStrategy {

    constructor(
        context: Context,
        recentDelayMs: Long = MAX_DELAY_BETWEEN_LOGS_MS,
        maxBatchSize: Long = MAX_BATCH_SIZE,
        maxLogPerBatch: Int = MAX_LOG_PER_BATCH,
        oldFileThreshold: Long = OLD_FILE_THRESHOLD,
        maxDiskSpace: Long = MAX_DISK_SPACE
    ) :
        this(
            rootDir = File(context.filesDir, LOGS_FOLDER_NAME),
            recentDelayMs = recentDelayMs,
            maxBatchSize = maxBatchSize,
            maxLogPerBatch = maxLogPerBatch,
            oldFileThreshold = oldFileThreshold,
            maxDiskSpace = maxDiskSpace
        )

    private val fileOrchestrator =
        FileOrchestrator(
            rootDirectory = rootDir,
            recentDelayMs = recentDelayMs,
            maxBatchSize = maxBatchSize,
            maxLogPerBatch = maxLogPerBatch,
            oldFileThreshold = oldFileThreshold,
            maxDiskSpace = maxDiskSpace
        )

    // region LogPersistingStrategy

    override fun getLogWriter(): Writer<Log> {
        return FileWriter(
            fileOrchestrator,
            rootDir,
            LogSerializer()
        )
    }

    override fun getLogReader(): Reader {
        return FileReader(
            fileOrchestrator,
            rootDir
        )
    }

    // endregion

    companion object {

        private const val MAX_BATCH_SIZE: Long = 4 * 1024 * 1024 // 4 MB
        internal const val MAX_LOG_PER_BATCH: Int = 500
        private const val OLD_FILE_THRESHOLD: Long = 18L * 60L * 60L * 1000L // 18 hours
        private const val MAX_DISK_SPACE: Long = 128 * MAX_BATCH_SIZE // 512 MB

        internal const val LOGS_FOLDER_NAME = "dd-logs"
        internal const val SEPARATOR_BYTE: Byte = '\n'.toByte()
        internal const val MAX_DELAY_BETWEEN_LOGS_MS = 5000L
    }
}
