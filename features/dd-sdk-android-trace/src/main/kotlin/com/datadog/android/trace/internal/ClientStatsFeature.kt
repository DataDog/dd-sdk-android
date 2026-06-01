/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal

import android.content.Context
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.trace.internal.net.ClientStatsRequestFactory
import com.datadog.trace.common.metrics.NoOpMetricsAggregator

internal class ClientStatsFeature(
    customEndpointUrl: String?
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

    // TODO RUM-16565 Implement stats aggregator
    internal val aggregator = NoOpMetricsAggregator()

    override val requestFactory = ClientStatsRequestFactory(customEndpointUrl)

    override fun onInitialize(appContext: Context) {}

    override fun onStop() {}
}
