/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.domain

import com.datadog.android.core.internal.data.Reader
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.data.file.FileOrchestrator
import com.datadog.android.core.internal.data.file.FileReader
import com.datadog.android.core.internal.domain.batching.ConsentAwareDataWriter
import com.datadog.android.core.internal.domain.batching.DataProcessorFactory
import com.datadog.android.core.internal.domain.batching.DefaultConsentAwareDataWriter
import com.datadog.android.core.internal.domain.batching.DefaultMigratorFactory
import com.datadog.android.core.internal.privacy.ConsentProvider
import java.io.File
import java.util.concurrent.ExecutorService

internal open class FilePersistenceStrategy<T : Any>(
    intermediateStorageFolder: File,
    authorizedStorageFolder: File,
    serializer: Serializer<T>,
    executorService: ExecutorService,
    filePersistenceConfig: FilePersistenceConfig = FilePersistenceConfig(),
    payloadDecoration: PayloadDecoration = PayloadDecoration.JSON_ARRAY_DECORATION,
    trackingConsentProvider: ConsentProvider
) : PersistenceStrategy<T> {

    internal val intermediateFileOrchestrator = FileOrchestrator(
        intermediateStorageFolder,
        filePersistenceConfig
    )

    internal val authorizedFileOrchestrator = FileOrchestrator(
        authorizedStorageFolder,
        filePersistenceConfig
    )

    internal val fileReader = FileReader(
        authorizedFileOrchestrator,
        authorizedStorageFolder,
        payloadDecoration.prefix,
        payloadDecoration.suffix
    )

    internal val consentAwareDataWriter: ConsentAwareDataWriter<T> =
        DefaultConsentAwareDataWriter(
            consentProvider = trackingConsentProvider,
            processorsFactory = DataProcessorFactory(
                intermediateFileOrchestrator,
                authorizedFileOrchestrator,
                serializer,
                payloadDecoration.separator,
                executorService
            ),
            migratorsFactory = DefaultMigratorFactory(
                intermediateStorageFolder.absolutePath,
                authorizedStorageFolder.absolutePath,
                executorService
            )
        )

    // region PersistenceStrategy

    override fun getWriter(): Writer<T> {
        return consentAwareDataWriter
    }

    override fun getReader(): Reader {
        return fileReader
    }

    override fun clearAllData() {
        fileReader.dropAllBatches()
    }

    // endregion
}
