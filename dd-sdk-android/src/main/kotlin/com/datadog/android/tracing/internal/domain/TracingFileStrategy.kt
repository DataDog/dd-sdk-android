package com.datadog.android.tracing.internal.domain

import android.content.Context
import com.datadog.android.core.internal.domain.FilePersistenceStrategy
import datadog.opentracing.DDSpan
import java.io.File

internal class TracingFileStrategy(
    context: Context,
    recentDelayMs: Long = MAX_DELAY_BETWEEN_LOGS_MS,
    maxBatchSize: Long = MAX_BATCH_SIZE,
    maxLogPerBatch: Int = MAX_ITEMS_PER_BATCH,
    oldFileThreshold: Long = OLD_FILE_THRESHOLD,
    maxDiskSpace: Long = MAX_DISK_SPACE
) : FilePersistenceStrategy<DDSpan>(
    File(
        context.filesDir,
        TRACES_FOLDER
    ),
    SpanSerializer(),
    WRITER_THREAD_NAME,
    recentDelayMs,
    maxBatchSize,
    maxLogPerBatch,
    oldFileThreshold,
    maxDiskSpace,
    "{ \"spans\": [",
    "]}"
) {

    companion object {
        internal const val TRACES_DATA_VERSION = 1
        internal const val DATA_FOLDER_ROOT = "dd-tracing"
        internal const val TRACES_FOLDER = "$DATA_FOLDER_ROOT-v$TRACES_DATA_VERSION"
        internal const val MAX_DELAY_BETWEEN_LOGS_MS = 5000L
        internal const val WRITER_THREAD_NAME = "ddog_traces_writer"
    }
}
