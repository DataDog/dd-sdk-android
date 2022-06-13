/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal.domain

import android.content.Context
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.persistence.PayloadDecoration
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.persistence.file.advanced.FeatureFileOrchestrator
import com.datadog.android.core.internal.persistence.file.batch.BatchFilePersistenceStrategy
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReaderWriter
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.event.SpanEventMapper
import com.datadog.android.log.Logger
import com.datadog.android.security.Encryption
import com.datadog.android.tracing.internal.TracingFeature
import com.datadog.android.tracing.internal.domain.event.DdSpanToSpanEventMapper
import com.datadog.android.tracing.internal.domain.event.SpanEventMapperWrapper
import com.datadog.android.tracing.internal.domain.event.SpanEventSerializer
import com.datadog.android.tracing.internal.domain.event.SpanMapperSerializer
import com.datadog.opentracing.DDSpan
import java.util.concurrent.ExecutorService

internal class TracesFilePersistenceStrategy(
    consentProvider: ConsentProvider,
    context: Context,
    executorService: ExecutorService,
    coreFeature: CoreFeature,
    envName: String,
    internalLogger: Logger,
    spanEventMapper: SpanEventMapper,
    localDataEncryption: Encryption?
) : BatchFilePersistenceStrategy<DDSpan>(
    FeatureFileOrchestrator(
        consentProvider,
        context,
        TracingFeature.TRACING_FEATURE_NAME,
        executorService,
        internalLogger
    ),
    executorService,
    SpanMapperSerializer(
        DdSpanToSpanEventMapper(
            coreFeature
        ),
        SpanEventMapperWrapper(spanEventMapper),
        SpanEventSerializer(envName)
    ),
    PayloadDecoration.NEW_LINE_DECORATION,
    internalLogger,
    BatchFileReaderWriter.create(internalLogger, localDataEncryption),
    FileMover(internalLogger)
)
