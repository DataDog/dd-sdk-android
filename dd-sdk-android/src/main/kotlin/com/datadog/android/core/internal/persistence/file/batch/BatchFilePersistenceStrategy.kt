/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import com.datadog.android.core.internal.domain.PayloadDecoration
import com.datadog.android.core.internal.persistence.DataReader
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.core.internal.persistence.Serializer
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.log.Logger

internal open class BatchFilePersistenceStrategy<T : Any>(
    private val fileOrchestrator: FileOrchestrator,
    serializer: Serializer<T>,
    payloadDecoration: PayloadDecoration,
    internalLogger: Logger
) : PersistenceStrategy<T> {

    internal val fileHandler = BatchFileHandler(internalLogger)

    private val fileWriter: DataWriter<T> by lazy {
        createWriter(
            fileOrchestrator,
            serializer,
            payloadDecoration,
            internalLogger
        )
    }

    private val fileReader = BatchFileDataReader(
        fileOrchestrator,
        payloadDecoration,
        fileHandler,
        internalLogger
    )

    // region PersistenceStrategy

    override fun getWriter(): DataWriter<T> {
        return fileWriter
    }

    override fun getReader(): DataReader {
        return fileReader
    }

    // endregion

    // region Open

    internal open fun createWriter(
        fileOrchestrator: FileOrchestrator,
        serializer: Serializer<T>,
        payloadDecoration: PayloadDecoration,
        internalLogger: Logger
    ): DataWriter<T> {
        return BatchFileDataWriter(
            fileOrchestrator,
            serializer,
            payloadDecoration,
            fileHandler
        )
    }

    //

    companion object {
        internal const val VERSION = 1
        internal const val PENDING_DIR = "dd-%s-pending-v$VERSION"
        internal const val AUTHORIZED_DIR = "dd-%s-v$VERSION"
    }
}
