/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.persistence.Serializer
import com.datadog.android.core.internal.persistence.file.advanced.ScheduledWriter
import com.datadog.android.core.internal.persistence.file.batch.BatchFilePersistenceStrategy
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReaderWriter
import com.datadog.android.event.EventMapper
import com.datadog.android.event.MapperSerializer
import com.datadog.android.log.Logger
import com.datadog.android.rum.internal.domain.event.RumEventSerializer
import com.datadog.android.security.Encryption
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.android.v2.core.internal.storage.Storage
import java.io.File
import java.util.concurrent.ExecutorService

internal class RumFilePersistenceStrategy(
    private val contextProvider: ContextProvider,
    eventMapper: EventMapper<Any>,
    executorService: ExecutorService,
    internalLogger: Logger,
    private val localDataEncryption: Encryption?,
    private val lastViewEventFile: File,
    private val storage: Storage
) : BatchFilePersistenceStrategy<Any>(
    contextProvider,
    executorService,
    MapperSerializer(
        eventMapper,
        RumEventSerializer()
    ),
    internalLogger,
    storage
) {

    override fun createWriter(
        executorService: ExecutorService,
        serializer: Serializer<Any>,
        internalLogger: Logger
    ): DataWriter<Any> {
        return ScheduledWriter(
            RumDataWriter(
                storage,
                contextProvider,
                serializer,
                BatchFileReaderWriter.create(internalLogger, localDataEncryption),
                internalLogger,
                lastViewEventFile
            ),
            executorService,
            internalLogger
        )
    }
}
