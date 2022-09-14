/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.error.internal

import com.datadog.android.core.internal.persistence.PayloadDecoration
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.file.advanced.FeatureFileOrchestrator
import com.datadog.android.core.internal.persistence.file.batch.BatchFilePersistenceStrategy
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReaderWriter
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.domain.event.LogEventSerializer
import com.datadog.android.log.model.LogEvent
import com.datadog.android.security.Encryption
import com.datadog.android.v2.core.internal.ContextProvider
import java.io.File
import java.util.concurrent.ExecutorService

internal class CrashReportFilePersistenceStrategy(
    contextProvider: ContextProvider,
    consentProvider: ConsentProvider,
    storageDir: File,
    executorService: ExecutorService,
    internalLogger: Logger,
    localDataEncryption: Encryption?
) : BatchFilePersistenceStrategy<LogEvent>(
    contextProvider,
    FeatureFileOrchestrator(
        consentProvider,
        storageDir,
        CrashReportsFeature.CRASH_FEATURE_NAME,
        executorService,
        internalLogger
    ),
    executorService,
    LogEventSerializer(),
    PayloadDecoration.JSON_ARRAY_DECORATION,
    sdkLogger,
    BatchFileReaderWriter.create(sdkLogger, localDataEncryption),
    FileReaderWriter.create(sdkLogger, localDataEncryption),
    FileMover(sdkLogger)
)
