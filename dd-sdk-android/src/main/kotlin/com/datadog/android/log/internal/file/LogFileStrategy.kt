/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.file

import android.content.Context
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.data.file.DeferredWriter
import com.datadog.android.core.internal.data.file.ImmediateFileWriter
import com.datadog.android.core.internal.domain.BasePersistenceStrategy
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.domain.LogSerializer
import java.io.File

internal class LogFileStrategy(
    context: Context,
    recentDelayMs: Long = MAX_DELAY_BETWEEN_LOGS_MS,
    maxBatchSize: Long = MAX_BATCH_SIZE,
    maxLogPerBatch: Int = MAX_LOG_PER_BATCH,
    oldFileThreshold: Long = OLD_FILE_THRESHOLD,
    maxDiskSpace: Long = MAX_DISK_SPACE
) : BasePersistenceStrategy<Log>(
    File(context.filesDir, LOGS_FOLDER),
    recentDelayMs,
    maxBatchSize,
    maxLogPerBatch,
    oldFileThreshold,
    maxDiskSpace
) {
    private val rootDir: File = context.filesDir

    private val fileWriter = ImmediateFileWriter(
        fileOrchestrator,
        LogSerializer()
    )

    // region LogFileStrategy

    override fun getSynchronousWriter(): Writer<Log> {
        return fileWriter
    }

    override fun getWriter(): Writer<Log> {
        return DeferredWriter(
            LogFileDataMigrator(rootDir),
            fileWriter
        )
    }

    // endregion

    companion object {

        private const val MAX_BATCH_SIZE: Long = 4 * 1024 * 1024 // 4 MB
        internal const val MAX_LOG_PER_BATCH: Int = 500
        private const val OLD_FILE_THRESHOLD: Long = 18L * 60L * 60L * 1000L // 18 hours
        private const val MAX_DISK_SPACE: Long = 128 * MAX_BATCH_SIZE // 512 MB

        internal const val LOGS_DATA_VERSION = 1
        internal const val DATA_FOLDER_ROOT = "dd-logs"
        internal const val LOGS_FOLDER = "$DATA_FOLDER_ROOT-v$LOGS_DATA_VERSION"
        internal const val MAX_DELAY_BETWEEN_LOGS_MS = 5000L
    }
}
