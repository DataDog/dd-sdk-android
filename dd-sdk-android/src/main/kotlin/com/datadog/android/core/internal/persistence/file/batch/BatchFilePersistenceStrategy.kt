/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.core.internal.persistence.Serializer
import com.datadog.android.core.internal.persistence.file.advanced.ScheduledWriter
import com.datadog.android.log.Logger
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.android.v2.core.internal.storage.Storage
import java.util.concurrent.ExecutorService

internal open class BatchFilePersistenceStrategy<T : Any>(
    private val contextProvider: ContextProvider,
    private val executorService: ExecutorService,
    serializer: Serializer<T>,
    internal val internalLogger: Logger,
    private val storage: Storage
) : PersistenceStrategy<T> {

    private val dataWriter: DataWriter<T> by lazy {
        createWriter(
            executorService,
            serializer,
            internalLogger
        )
    }

    // region PersistenceStrategy

    override fun getWriter(): DataWriter<T> {
        return dataWriter
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
                storage,
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
