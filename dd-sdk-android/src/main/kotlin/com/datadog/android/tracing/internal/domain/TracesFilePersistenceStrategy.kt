/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal.domain

import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.persistence.file.batch.BatchFilePersistenceStrategy
import com.datadog.android.event.SpanEventMapper
import com.datadog.android.log.Logger
import com.datadog.android.tracing.internal.domain.event.DdSpanToSpanEventMapper
import com.datadog.android.tracing.internal.domain.event.SpanEventMapperWrapper
import com.datadog.android.tracing.internal.domain.event.SpanEventSerializer
import com.datadog.android.tracing.internal.domain.event.SpanMapperSerializer
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.android.v2.core.internal.storage.Storage
import com.datadog.opentracing.DDSpan
import java.util.concurrent.ExecutorService

internal class TracesFilePersistenceStrategy(
    contextProvider: ContextProvider,
    executorService: ExecutorService,
    coreFeature: CoreFeature,
    envName: String,
    internalLogger: Logger,
    spanEventMapper: SpanEventMapper,
    storage: Storage
) : BatchFilePersistenceStrategy<DDSpan>(
    contextProvider,
    executorService,
    SpanMapperSerializer(
        DdSpanToSpanEventMapper(
            coreFeature
        ),
        SpanEventMapperWrapper(spanEventMapper),
        SpanEventSerializer(envName)
    ),
    internalLogger,
    storage
)
