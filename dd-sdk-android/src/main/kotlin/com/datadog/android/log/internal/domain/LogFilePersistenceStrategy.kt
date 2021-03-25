/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.domain

import android.content.Context
import com.datadog.android.core.internal.persistence.PayloadDecoration
import com.datadog.android.core.internal.persistence.file.advanced.FeatureFileOrchestrator
import com.datadog.android.core.internal.persistence.file.batch.BatchFilePersistenceStrategy
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.log.Logger
import java.util.concurrent.ExecutorService

internal class LogFilePersistenceStrategy(
    consentProvider: ConsentProvider,
    context: Context,
    executorService: ExecutorService,
    internalLogger: Logger
) :
    BatchFilePersistenceStrategy<Log>(
        FeatureFileOrchestrator(
            consentProvider,
            context,
            "logs",
            executorService,
            internalLogger
        ),
        executorService,
        LogSerializer(),
        PayloadDecoration.JSON_ARRAY_DECORATION,
        sdkLogger
    )
