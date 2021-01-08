/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.domain.batching

import com.datadog.android.core.internal.data.Orchestrator
import com.datadog.android.core.internal.data.file.DefaultFileWriter
import com.datadog.android.core.internal.domain.Serializer
import com.datadog.android.core.internal.domain.batching.processors.DataProcessor
import com.datadog.android.core.internal.domain.batching.processors.DefaultDataProcessor
import com.datadog.android.core.internal.domain.batching.processors.NoOpDataProcessor
import com.datadog.android.core.internal.event.NoOpEventMapper
import com.datadog.android.event.EventMapper
import com.datadog.android.privacy.TrackingConsent
import java.util.concurrent.ExecutorService

internal class DataProcessorFactory<T : Any>(
    private val intermediateFileOrchestrator: Orchestrator,
    private val targetFileOrchestrator: Orchestrator,
    private val serializer: Serializer<T>,
    private val separator: CharSequence,
    private val executorService: ExecutorService,
    private val eventMapper: EventMapper<T> = NoOpEventMapper()
) {

    fun resolveProcessor(consent: TrackingConsent): DataProcessor<T> {
        return when (consent) {
            TrackingConsent.PENDING -> {
                intermediateFileOrchestrator.reset()
                DefaultDataProcessor(
                    executorService,
                    DefaultFileWriter(intermediateFileOrchestrator, serializer, separator),
                    eventMapper
                )
            }
            TrackingConsent.GRANTED -> {
                DefaultDataProcessor(
                    executorService,
                    DefaultFileWriter(targetFileOrchestrator, serializer, separator),
                    eventMapper
                )
            }
            else -> {
                NoOpDataProcessor()
            }
        }
    }
}
