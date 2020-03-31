/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.error.internal

import android.content.Context
import com.datadog.android.core.internal.domain.FilePersistenceStrategy
import com.datadog.android.core.internal.domain.PayloadDecoration
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.domain.LogFileStrategy
import com.datadog.android.log.internal.domain.LogSerializer
import java.io.File

internal class CrashLogFileStrategy(
    context: Context,
    recentDelayMs: Long = MAX_DELAY_BETWEEN_LOGS_MS,
    maxBatchSize: Long = MAX_BATCH_SIZE,
    maxLogPerBatch: Int = MAX_ITEMS_PER_BATCH,
    oldFileThreshold: Long = OLD_FILE_THRESHOLD,
    maxDiskSpace: Long = MAX_DISK_SPACE
) : FilePersistenceStrategy<Log>(
    File(context.filesDir, CRASH_REPORTS_FOLDER),
    LogSerializer(),
    recentDelayMs,
    maxBatchSize,
    maxLogPerBatch,
    oldFileThreshold,
    maxDiskSpace,
    PayloadDecoration.JSON_ARRAY_DECORATION
) {
    companion object {
        internal const val CRASH_REPORTS_DATA_VERSION = LogFileStrategy.LOGS_DATA_VERSION
        internal const val DATA_FOLDER_ROOT = "dd-crash"
        internal const val CRASH_REPORTS_FOLDER = "$DATA_FOLDER_ROOT-v$CRASH_REPORTS_DATA_VERSION"
        internal const val MAX_DELAY_BETWEEN_LOGS_MS = 5000L
    }
}
