/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal

import android.content.Context
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.trace.event.SpanEventMapper
import com.datadog.android.trace.internal.domain.event.CoreTracerSpanToSpanEventMapper
import com.datadog.android.trace.internal.domain.event.SpanEventMapperWrapper
import com.datadog.android.trace.internal.domain.metrics.BatchStatsWriter
import com.datadog.android.trace.internal.domain.metrics.ClientStatsAdapter
import com.datadog.android.trace.internal.domain.metrics.StatsConcentrator
import com.datadog.android.trace.internal.net.ClientStatsRequestFactory
import com.datadog.trace.api.Config
import java.util.concurrent.ScheduledExecutorService

internal class ClientStatsFeature(
    private val sdkCore: FeatureSdkCore,
    customEndpointUrl: String?,
    spanEventMapper: SpanEventMapper,
    networkInfoEnabled: Boolean
) : StorageBackedFeature {
    override val name = Feature.TRACING_CLIENT_STATS_FEATURE_NAME

    override val storageConfiguration = FeatureStorageConfiguration(
        // 512 KB
        maxItemSize = 512L * 1024,
        maxItemsPerBatch = 4000,
        // 15 MB
        maxBatchSize = 15L * 1024 * 1024,
        // 18 hours
        oldBatchThreshold = 18L * 60L * 60L * 1000L
    )

    private val statsExecutor: ScheduledExecutorService by lazy {
        sdkCore.createScheduledExecutorService("client-side-stats-aggregator")
    }
    private val statsConcentrator by lazy {
        // runtimeId is static and encapsulated in Tracer core's Config. There is no way to get it except via the root
        // span's tags or this Config object
        val runtimeID = Config.get().runtimeId

        StatsConcentrator(
            sdkCore,
            ddSpanToSpanEventMapper = CoreTracerSpanToSpanEventMapper(networkInfoEnabled),
            eventMapper = SpanEventMapperWrapper(spanEventMapper, sdkCore.internalLogger),
            statsExecutor,
            BatchStatsWriter(sdkCore, runtimeID),
            sdkCore.timeProvider
        )
    }

    internal val aggregator by lazy {
        ClientStatsAdapter(statsConcentrator)
    }

    override val requestFactory = ClientStatsRequestFactory(customEndpointUrl)

    override fun onInitialize(appContext: Context) {}

    override fun onStop() {
        statsConcentrator.stop()

        statsExecutor.shutdown()
    }
}
