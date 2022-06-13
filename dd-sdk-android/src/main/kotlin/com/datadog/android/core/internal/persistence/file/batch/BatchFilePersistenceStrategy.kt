/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import com.datadog.android.core.internal.data.upload.DataFlusher
import com.datadog.android.core.internal.data.upload.Flusher
import com.datadog.android.core.internal.persistence.DataReader
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.persistence.PayloadDecoration
import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.core.internal.persistence.Serializer
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.advanced.ScheduledWriter
import com.datadog.android.log.Logger
import java.util.concurrent.ExecutorService

internal open class BatchFilePersistenceStrategy<T : Any>(
    private val fileOrchestrator: FileOrchestrator,
    private val executorService: ExecutorService,
    serializer: Serializer<T>,
    private val payloadDecoration: PayloadDecoration,
    internalLogger: Logger,
    internal val fileReaderWriter: BatchFileReaderWriter,
    val fileMover: FileMover
) : PersistenceStrategy<T> {

    private val dataWriter: DataWriter<T> by lazy {
        createWriter(
            fileOrchestrator,
            executorService,
            serializer,
            internalLogger
        )
    }

    private val dataReader = BatchFileDataReader(
        fileOrchestrator,
        payloadDecoration,
        fileReaderWriter,
        fileMover,
        internalLogger
    )

    // region PersistenceStrategy

    override fun getWriter(): DataWriter<T> {
        return dataWriter
    }

    override fun getReader(): DataReader {
        return dataReader
    }

    override fun getFlusher(): Flusher {
        return DataFlusher(fileOrchestrator, payloadDecoration, fileReaderWriter, fileMover)
    }

    // endregion

    // region Open

    internal open fun createWriter(
        fileOrchestrator: FileOrchestrator,
        executorService: ExecutorService,
        serializer: Serializer<T>,
        internalLogger: Logger
    ): DataWriter<T> {
        return ScheduledWriter(
            BatchFileDataWriter(
                fileOrchestrator,
                serializer,
                fileReaderWriter,
                internalLogger
            ),
            executorService,
            internalLogger
        )
    }

    //
}
