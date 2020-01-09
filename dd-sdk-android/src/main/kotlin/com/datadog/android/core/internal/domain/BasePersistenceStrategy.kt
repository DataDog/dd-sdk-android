/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.core.internal.domain

import com.datadog.android.core.internal.data.DataMigrator
import com.datadog.android.core.internal.data.Reader
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.data.file.DeferredWriter
import com.datadog.android.core.internal.data.file.FileOrchestrator
import com.datadog.android.core.internal.data.file.FileReader
import com.datadog.android.core.internal.data.file.ImmediateFileWriter
import java.io.File

internal abstract class BasePersistenceStrategy<T : Any>(
    dataDirectory: File,
    recentDelayMs: Long,
    maxBatchSize: Long,
    maxItemsPerBatch: Int,
    oldFileThreshold: Long,
    maxDiskSpace: Long,
    private val dataMigrator: DataMigrator,
    serializer: Serializer<T>
) : PersistenceStrategy<T> {

    val fileOrchestrator = FileOrchestrator(
        rootDirectory = dataDirectory,
        recentDelayMs = recentDelayMs,
        maxBatchSize = maxBatchSize,
        maxLogPerBatch = maxItemsPerBatch,
        oldFileThreshold = oldFileThreshold,
        maxDiskSpace = maxDiskSpace
    )

    private val fileReader = FileReader(
        fileOrchestrator,
        dataDirectory
    )

    // region Strategy methods

    private val fileWriter = ImmediateFileWriter(
        fileOrchestrator,
        serializer
    )

    override fun getSynchronousWriter(): Writer<T> {
        return fileWriter
    }

    override fun getWriter(): Writer<T> {
        return DeferredWriter(
            dataMigrator,
            fileWriter
        )
    }

    override fun getReader(): Reader {
        return fileReader
    }

    // endregion
}
