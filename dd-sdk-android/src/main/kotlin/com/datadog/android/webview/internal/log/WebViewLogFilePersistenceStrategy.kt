/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.log

import android.content.Context
import com.datadog.android.core.internal.persistence.PayloadDecoration
import com.datadog.android.core.internal.persistence.file.advanced.FeatureFileOrchestrator
import com.datadog.android.core.internal.persistence.file.batch.BatchFileHandler
import com.datadog.android.core.internal.persistence.file.batch.BatchFilePersistenceStrategy
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.domain.event.WebViewLogEventSerializer
import com.datadog.android.log.model.WebViewLogEvent
import com.datadog.android.security.Encryption
import java.util.concurrent.ExecutorService

internal class WebViewLogFilePersistenceStrategy(
    consentProvider: ConsentProvider,
    context: Context,
    executorService: ExecutorService,
    internalLogger: Logger,
    localDataEncryption: Encryption?
) :
    BatchFilePersistenceStrategy<WebViewLogEvent>(
        FeatureFileOrchestrator(
            consentProvider,
            context,
            WebViewLogsFeature.WEB_LOGS_FEATURE_NAME,
            executorService,
            internalLogger
        ),
        executorService,
        WebViewLogEventSerializer(),
        PayloadDecoration.JSON_ARRAY_DECORATION,
        sdkLogger,
        BatchFileHandler.create(sdkLogger, localDataEncryption)
    )
