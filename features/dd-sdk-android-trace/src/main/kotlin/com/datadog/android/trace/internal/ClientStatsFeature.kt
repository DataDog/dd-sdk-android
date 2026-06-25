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
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.privacy.TrackingConsentProviderCallback
import com.datadog.android.trace.event.SpanEventMapper
import com.datadog.android.trace.internal.domain.event.CoreTracerSpanToSpanEventMapper
import com.datadog.android.trace.internal.domain.event.SpanEventMapperWrapper
import com.datadog.android.trace.internal.domain.metrics.BatchStatsWriter
import com.datadog.android.trace.internal.domain.metrics.ClientStatsAdapter
import com.datadog.android.trace.internal.domain.metrics.StatsConcentrator
import com.datadog.android.trace.internal.net.ClientStatsRequestFactory
import com.datadog.trace.api.Config
import com.datadog.trace.common.metrics.MetricsAggregator
import com.datadog.trace.common.metrics.NoOpMetricsAggregator
import java.util.concurrent.ScheduledExecutorService

internal class ClientStatsFeature(
    private val sdkCore: FeatureSdkCore,
    customEndpointUrl: String?,
    private val spanEventMapper: SpanEventMapper,
    private val networkInfoEnabled: Boolean
) : StorageBackedFeature, TrackingConsentProviderCallback {
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

    @Volatile
    internal var aggregator: MetricsAggregator = NoOpMetricsAggregator()

    private var statsConcentrator: StatsConcentrator? = null
    private var statsExecutor: ScheduledExecutorService? = null

    override val requestFactory = ClientStatsRequestFactory(customEndpointUrl)

    override fun onInitialize(appContext: Context) {
        val internalSdkCore = sdkCore as InternalSdkCore
        val executor = sdkCore.createScheduledExecutorService("client-side-stats-aggregator")
        val concentrator = StatsConcentrator(
            sdkCore = internalSdkCore,
            ddSpanToSpanEventMapper = CoreTracerSpanToSpanEventMapper(networkInfoEnabled),
            eventMapper = SpanEventMapperWrapper(spanEventMapper, sdkCore.internalLogger),
            executorService = executor,
            statsWriter = BatchStatsWriter(sdkCore, Config.get().runtimeId),
            timeProvider = internalSdkCore.timeProvider,
            initialConsent = internalSdkCore.trackingConsent
        )
        statsConcentrator = concentrator
        statsExecutor = executor
        aggregator = ClientStatsAdapter(concentrator)
    }

    override fun onStop() {
        statsConcentrator?.stop()
        statsExecutor?.shutdown()
    }

    override fun onConsentUpdated(
        previousConsent: TrackingConsent,
        newConsent: TrackingConsent
    ) {
        statsConcentrator?.onConsentUpdated(newConsent)
    }
}
