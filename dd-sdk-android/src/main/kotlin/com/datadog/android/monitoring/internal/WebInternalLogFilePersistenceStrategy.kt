/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.monitoring.internal

import android.content.Context
import com.datadog.android.core.internal.persistence.PayloadDecoration
import com.datadog.android.core.internal.persistence.file.advanced.FeatureFileOrchestrator
import com.datadog.android.core.internal.persistence.file.batch.BatchFilePersistenceStrategy
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.domain.event.WebLogEventSerializer
import com.datadog.android.log.model.WebLogEvent
import java.util.concurrent.ExecutorService

internal class WebInternalLogFilePersistenceStrategy(
    consentProvider: ConsentProvider,
    context: Context,
    executorService: ExecutorService,
    internalLogger: Logger
) : BatchFilePersistenceStrategy<WebLogEvent>(
    FeatureFileOrchestrator(
        consentProvider,
        context,
        WebInternalLogsFeature.WEB_INTERNAL_LOGS_FEATURE_NAME,
        executorService,
        internalLogger
    ),
    executorService,
    WebLogEventSerializer(),
    PayloadDecoration.JSON_ARRAY_DECORATION,
    internalLogger
)
