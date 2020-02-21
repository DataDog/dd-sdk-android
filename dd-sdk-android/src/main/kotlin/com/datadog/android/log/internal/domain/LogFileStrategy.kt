/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.domain

import android.content.Context
import com.datadog.android.core.internal.domain.AsyncWriterFilePersistenceStrategy
import com.datadog.android.core.internal.domain.PayloadDecoration
import java.io.File

internal class LogFileStrategy(
    context: Context,
    recentDelayMs: Long = MAX_DELAY_BETWEEN_LOGS_MS,
    maxBatchSize: Long = MAX_BATCH_SIZE,
    maxLogPerBatch: Int = MAX_ITEMS_PER_BATCH,
    oldFileThreshold: Long = OLD_FILE_THRESHOLD,
    maxDiskSpace: Long = MAX_DISK_SPACE
) : AsyncWriterFilePersistenceStrategy<Log>(
    File(context.filesDir, LOGS_FOLDER),
    LogSerializer(),
    recentDelayMs,
    maxBatchSize,
    maxLogPerBatch,
    oldFileThreshold,
    maxDiskSpace,
    PayloadDecoration.JSON_ARRAY_DECORATION,
    WRITER_THREAD_NAME,
    LogFileDataMigrator(context.filesDir)
) {
    companion object {
        internal const val LOGS_DATA_VERSION = 1
        internal const val DATA_FOLDER_ROOT = "dd-logs"
        internal const val LOGS_FOLDER = "$DATA_FOLDER_ROOT-v$LOGS_DATA_VERSION"
        internal const val MAX_DELAY_BETWEEN_LOGS_MS = 5000L
        internal const val WRITER_THREAD_NAME = "ddog_logs_writer"
    }
}
