/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.rum

import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.persistence.Serializer
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.file.advanced.FeatureFileOrchestrator
import com.datadog.android.core.internal.persistence.file.advanced.ScheduledWriter
import com.datadog.android.core.internal.persistence.file.batch.BatchFilePersistenceStrategy
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReaderWriter
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.log.Logger
import com.datadog.android.rum.internal.domain.RumDataWriter
import com.datadog.android.rum.internal.domain.event.RumEventSerializer
import com.datadog.android.security.Encryption
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.android.v2.core.internal.storage.Storage
import java.io.File
import java.util.concurrent.ExecutorService

internal class WebViewRumFilePersistenceStrategy(
    private val contextProvider: ContextProvider,
    consentProvider: ConsentProvider,
    storageDir: File,
    executorService: ExecutorService,
    internalLogger: Logger,
    localDataEncryption: Encryption?,
    private val lastViewEventFile: File,
    filePersistenceConfig: FilePersistenceConfig,
    storage: Storage
) : BatchFilePersistenceStrategy<Any>(
    contextProvider,
    FeatureFileOrchestrator(
        consentProvider,
        storageDir,
        WebViewRumFeature.WEB_RUM_FEATURE_NAME,
        executorService,
        internalLogger
    ),
    executorService,
    RumEventSerializer(),
    internalLogger,
    BatchFileReaderWriter.create(internalLogger, localDataEncryption),
    FileReaderWriter.create(internalLogger, localDataEncryption),
    FileMover(internalLogger),
    filePersistenceConfig,
    storage
) {

    override fun createWriter(
        executorService: ExecutorService,
        serializer: Serializer<Any>,
        internalLogger: Logger
    ): DataWriter<Any> {
        return ScheduledWriter(
            RumDataWriter(
                getStorage(),
                contextProvider,
                serializer,
                fileReaderWriter,
                internalLogger,
                lastViewEventFile
            ),
            executorService,
            internalLogger
        )
    }
}
