/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.domain

import com.datadog.android.core.internal.data.DataMigrator
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.data.file.DeferredWriter
import java.io.File
import java.util.concurrent.ExecutorService

internal abstract class AsyncWriterFilePersistenceStrategy<T : Any>(
    dataDirectory: File,
    serializer: Serializer<T>,
    filePersistenceConfig: FilePersistenceConfig = FilePersistenceConfig(),
    payloadDecoration: PayloadDecoration = PayloadDecoration.JSON_ARRAY_DECORATION,
    private val dataMigrator: DataMigrator? = null,
    private val dataPersistenceExecutorService: ExecutorService
) : FilePersistenceStrategy<T>(
    dataDirectory,
    serializer,
    filePersistenceConfig,
    payloadDecoration
) {

    val deferredWriter: DeferredWriter<T> by lazy {
        DeferredWriter(
            fileWriter,
            dataPersistenceExecutorService,
            dataMigrator
        )
    }

    override fun getWriter(): Writer<T> {
        return deferredWriter
    }
}
