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
import java.io.File

internal abstract class BasePersistenceStrategy<T : Any>(
    dataDirectory: File,
    recentDelayMs: Long,
    maxBatchSize: Long,
    maxLogPerBatch: Int,
    oldFileThreshold: Long,
    maxDiskSpace: Long
) : PersistenceStrategy<T> {

    val fileOrchestrator = FileOrchestrator(
        rootDirectory = dataDirectory,
        recentDelayMs = recentDelayMs,
        maxBatchSize = maxBatchSize,
        maxLogPerBatch = maxLogPerBatch,
        oldFileThreshold = oldFileThreshold,
        maxDiskSpace = maxDiskSpace
    )

    private val fileReader = FileReader(
        fileOrchestrator,
        dataDirectory
    )

    // region Strategy methods

    abstract override fun getWriter(): Writer<T>

    abstract override fun getSynchronousWriter(): Writer<T>

    override fun getReader(): Reader {
        return fileReader
    }

    // endregion
}
