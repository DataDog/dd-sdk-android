/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import com.datadog.android.core.internal.persistence.DataReader
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.persistence.PayloadDecoration
import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.core.internal.persistence.Serializer
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.file.advanced.ConsentAwareFileOrchestrator
import com.datadog.android.core.internal.persistence.file.advanced.ScheduledWriter
import com.datadog.android.log.Logger
import com.datadog.android.v2.api.NoOpInternalLogger
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.android.v2.core.internal.data.upload.DataFlusher
import com.datadog.android.v2.core.internal.data.upload.Flusher
import com.datadog.android.v2.core.internal.storage.ConsentAwareStorage
import com.datadog.android.v2.core.internal.storage.Storage
import java.util.concurrent.ExecutorService

internal open class BatchFilePersistenceStrategy<T : Any>(
    private val contextProvider: ContextProvider,
    private val fileOrchestrator: ConsentAwareFileOrchestrator,
    private val executorService: ExecutorService,
    serializer: Serializer<T>,
    payloadDecoration: PayloadDecoration,
    internal val internalLogger: Logger,
    internal val fileReaderWriter: BatchFileReaderWriter,
    internal val metadataFileReaderWriter: FileReaderWriter,
    internal val fileMover: FileMover,
    internal val filePersistenceConfig: FilePersistenceConfig
) : PersistenceStrategy<T> {

    private val dataWriter: DataWriter<T> by lazy {
        createWriter(
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

    private val dataStorage = ConsentAwareStorage(
        grantedOrchestrator = fileOrchestrator.grantedOrchestrator,
        pendingOrchestrator = fileOrchestrator.pendingOrchestrator,
        batchEventsReaderWriter = fileReaderWriter,
        batchMetadataReaderWriter = metadataFileReaderWriter,
        fileMover = fileMover,
        // TODO RUMM-0000 create internal logger
        internalLogger = NoOpInternalLogger(),
        filePersistenceConfig = filePersistenceConfig
    )

    // region PersistenceStrategy

    override fun getWriter(): DataWriter<T> {
        return dataWriter
    }

    override fun getReader(): DataReader {
        return dataReader
    }

    override fun getStorage(): Storage {
        return dataStorage
    }

    override fun getFlusher(): Flusher {
        return DataFlusher(
            contextProvider,
            fileOrchestrator,
            fileReaderWriter,
            metadataFileReaderWriter,
            fileMover
        )
    }

    // endregion

    // region Open

    internal open fun createWriter(
        executorService: ExecutorService,
        serializer: Serializer<T>,
        internalLogger: Logger
    ): DataWriter<T> {
        return ScheduledWriter(
            BatchFileDataWriter(
                dataStorage,
                contextProvider,
                serializer,
                internalLogger
            ),
            executorService,
            internalLogger
        )
    }

    // endregion
}
