/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.core.internal.domain

import com.datadog.android.core.internal.data.Reader
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.data.file.FileOrchestrator
import com.datadog.android.core.internal.data.file.FileReader
import com.datadog.android.core.internal.data.file.ImmediateFileWriter
import java.io.File

internal open class FilePersistenceStrategy<T : Any>(
    dataDirectory: File,
    serializer: Serializer<T>,
    recentDelayMs: Long = MAX_DELAY_BETWEEN_MESSAGES_MS,
    maxBatchSize: Long = MAX_BATCH_SIZE,
    maxItemsPerBatch: Int = MAX_ITEMS_PER_BATCH,
    oldFileThreshold: Long = OLD_FILE_THRESHOLD,
    maxDiskSpace: Long = MAX_DISK_SPACE,
    payloadPrefix: CharSequence = "",
    payloadSuffix: CharSequence = ""
) : PersistenceStrategy<T> {

    private val fileOrchestrator = FileOrchestrator(
        rootDirectory = dataDirectory,
        recentDelayMs = recentDelayMs,
        maxBatchSize = maxBatchSize,
        maxLogPerBatch = maxItemsPerBatch,
        oldFileThreshold = oldFileThreshold,
        maxDiskSpace = maxDiskSpace
    )

    private val fileReader = FileReader(
        fileOrchestrator,
        dataDirectory,
        payloadPrefix,
        payloadSuffix
    )

    // region Strategy methods

    protected val fileWriter = ImmediateFileWriter(
        fileOrchestrator,
        serializer
    )

    override fun getWriter(): Writer<T> {
        return fileWriter
    }

    override fun getReader(): Reader {
        return fileReader
    }

    // endregion

    companion object {
        internal const val MAX_BATCH_SIZE: Long = 4 * 1024 * 1024 // 4 MB
        internal const val MAX_ITEMS_PER_BATCH: Int = 500
        internal const val OLD_FILE_THRESHOLD: Long = 18L * 60L * 60L * 1000L // 18 hours
        internal const val MAX_DISK_SPACE: Long = 128 * MAX_BATCH_SIZE // 512 MB
        internal const val MAX_DELAY_BETWEEN_MESSAGES_MS = 5000L
    }
}
