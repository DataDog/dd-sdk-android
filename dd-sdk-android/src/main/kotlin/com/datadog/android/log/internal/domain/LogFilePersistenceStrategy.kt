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
import com.datadog.android.event.EventMapper
import com.datadog.android.event.MapperSerializer
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.LogsFeature
import com.datadog.android.log.internal.domain.event.LogEventMapperWrapper
import com.datadog.android.log.internal.domain.event.LogEventSerializer
import com.datadog.android.log.model.LogEvent
import java.util.concurrent.ExecutorService

internal class LogFilePersistenceStrategy(
    consentProvider: ConsentProvider,
    context: Context,
    executorService: ExecutorService,
    internalLogger: Logger,
    logEventMapper: EventMapper<LogEvent>
) :
    BatchFilePersistenceStrategy<LogEvent>(
        FeatureFileOrchestrator(
            consentProvider,
            context,
            LogsFeature.LOGS_FEATURE_NAME,
            executorService,
            internalLogger
        ),
        executorService,
        MapperSerializer(LogEventMapperWrapper(logEventMapper), LogEventSerializer()),
        PayloadDecoration.JSON_ARRAY_DECORATION,
        sdkLogger
    )
