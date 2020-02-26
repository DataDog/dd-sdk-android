package com.datadog.android.rum.internal.domain

import android.content.Context
import com.datadog.android.core.internal.domain.AsyncWriterFilePersistenceStrategy
import com.datadog.android.core.internal.domain.PayloadDecoration
import java.io.File

internal class RumFileStrategy(
    context: Context,
    recentDelayMs: Long = MAX_DELAY_BETWEEN_LOGS_MS,
    maxBatchSize: Long = MAX_BATCH_SIZE,
    maxLogPerBatch: Int = MAX_ITEMS_PER_BATCH,
    oldFileThreshold: Long = OLD_FILE_THRESHOLD,
    maxDiskSpace: Long = MAX_DISK_SPACE
) : AsyncWriterFilePersistenceStrategy<RumEvent>(
    File(context.filesDir, RUM_FOLDER),
    RumEventSerializer(),
    recentDelayMs,
    maxBatchSize,
    maxLogPerBatch,
    oldFileThreshold,
    maxDiskSpace,
    PayloadDecoration.NEW_LINE_DECORATION,
    WRITER_THREAD_NAME
) {
    companion object {
        internal const val TRACES_DATA_VERSION = 1
        internal const val DATA_FOLDER_ROOT = "dd-rum"
        internal const val RUM_FOLDER = "$DATA_FOLDER_ROOT-v$TRACES_DATA_VERSION"
        internal const val MAX_DELAY_BETWEEN_LOGS_MS = 5000L
        internal const val WRITER_THREAD_NAME = "ddog_rum_writer"
    }
}
