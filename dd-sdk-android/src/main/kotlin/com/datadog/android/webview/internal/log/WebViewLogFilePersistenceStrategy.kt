/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.log

import com.datadog.android.core.internal.persistence.PayloadDecoration
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.file.advanced.FeatureFileOrchestrator
import com.datadog.android.core.internal.persistence.file.batch.BatchFilePersistenceStrategy
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReaderWriter
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.domain.event.WebViewLogEventSerializer
import com.datadog.android.security.Encryption
import com.datadog.android.v2.core.internal.ContextProvider
import com.google.gson.JsonObject
import java.io.File
import java.util.concurrent.ExecutorService

internal class WebViewLogFilePersistenceStrategy(
    contextProvider: ContextProvider,
    consentProvider: ConsentProvider,
    storageDir: File,
    executorService: ExecutorService,
    internalLogger: Logger,
    localDataEncryption: Encryption?
) : BatchFilePersistenceStrategy<JsonObject>(
    contextProvider,
    FeatureFileOrchestrator(
        consentProvider,
        storageDir,
        WebViewLogsFeature.WEB_LOGS_FEATURE_NAME,
        executorService,
        internalLogger
    ),
    executorService,
    WebViewLogEventSerializer(),
    PayloadDecoration.JSON_ARRAY_DECORATION,
    sdkLogger,
    BatchFileReaderWriter.create(internalLogger, localDataEncryption),
    FileReaderWriter.create(internalLogger, localDataEncryption),
    FileMover(internalLogger)
)
