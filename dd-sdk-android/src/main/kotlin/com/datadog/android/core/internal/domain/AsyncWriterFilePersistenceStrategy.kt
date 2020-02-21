/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.core.internal.domain

import com.datadog.android.core.internal.data.DataMigrator
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.data.file.DeferredWriter
import java.io.File

internal abstract class AsyncWriterFilePersistenceStrategy<T : Any>(
    dataDirectory: File,
    serializer: Serializer<T>,
    recentDelayMs: Long = MAX_DELAY_BETWEEN_MESSAGES_MS,
    maxBatchSize: Long = MAX_BATCH_SIZE,
    maxItemsPerBatch: Int = MAX_ITEMS_PER_BATCH,
    oldFileThreshold: Long = OLD_FILE_THRESHOLD,
    maxDiskSpace: Long = MAX_DISK_SPACE,
    payloadDecoration: PayloadDecoration = PayloadDecoration.JSON_ARRAY_DECORATION,
    private val writerThreadName: String,
    private val dataMigrator: DataMigrator? = null
) : FilePersistenceStrategy<T>(
    dataDirectory,
    serializer,
    recentDelayMs,
    maxBatchSize,
    maxItemsPerBatch,
    oldFileThreshold,
    maxDiskSpace,
    payloadDecoration
) {

    val deferredWriter: DeferredWriter<T> by lazy {
        DeferredWriter(
            writerThreadName,
            fileWriter,
            dataMigrator
        )
    }

    override fun getWriter(): Writer<T> {
        return deferredWriter
    }
}
