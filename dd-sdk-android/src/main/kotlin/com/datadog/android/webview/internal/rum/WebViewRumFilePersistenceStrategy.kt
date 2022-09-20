/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.rum

import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.persistence.PayloadDecoration
import com.datadog.android.core.internal.persistence.Serializer
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
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
import java.io.File
import java.util.concurrent.ExecutorService

internal class WebViewRumFilePersistenceStrategy(
    contextProvider: ContextProvider,
    consentProvider: ConsentProvider,
    storageDir: File,
    executorService: ExecutorService,
    internalLogger: Logger,
    localDataEncryption: Encryption?,
    private val lastViewEventFile: File
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
    PayloadDecoration.NEW_LINE_DECORATION,
    internalLogger,
    BatchFileReaderWriter.create(internalLogger, localDataEncryption),
    FileReaderWriter.create(internalLogger, localDataEncryption),
    FileMover(internalLogger)
) {

    override fun createWriter(
        fileOrchestrator: FileOrchestrator,
        executorService: ExecutorService,
        serializer: Serializer<Any>,
        internalLogger: Logger
    ): DataWriter<Any> {
        return ScheduledWriter(
            RumDataWriter(
                fileOrchestrator,
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
