/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal

import android.content.Context
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureEventReceiver
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
import java.util.Locale
import java.util.concurrent.ScheduledExecutorService

internal class ClientStatsFeature(
    private val sdkCore: FeatureSdkCore,
    customEndpointUrl: String?,
    private val spanEventMapper: SpanEventMapper,
    private val networkInfoEnabled: Boolean
) : StorageBackedFeature, TrackingConsentProviderCallback, FeatureEventReceiver {
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
            ddSpanToSpanEventMapper = CoreTracerSpanToSpanEventMapper(
                networkInfoEnabled,
                clientSideStatsEnabled = true
            ),
            eventMapper = SpanEventMapperWrapper(spanEventMapper, sdkCore.internalLogger),
            executorService = executor,
            statsWriter = BatchStatsWriter(sdkCore, Config.get().runtimeId),
            timeProvider = internalSdkCore.timeProvider,
            initialConsent = internalSdkCore.trackingConsent
        )
        statsConcentrator = concentrator
        statsExecutor = executor
        aggregator = ClientStatsAdapter(concentrator)

        sdkCore.setEventReceiver(name, this)
    }

    override fun onStop() {
        sdkCore.removeEventReceiver(name)
        statsConcentrator?.stop()
        statsExecutor?.shutdown()
    }

    override fun onConsentUpdated(
        previousConsent: TrackingConsent,
        newConsent: TrackingConsent
    ) {
        statsConcentrator?.onConsentUpdated(newConsent)
    }

    override fun onReceive(event: Any) {
        if (event !is Map<*, *>) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { UNSUPPORTED_EVENT_TYPE.format(Locale.US, event::class.java.canonicalName) }
            )
            return
        }

        when (event["type"]) {
            FLUSH_AND_STOP_STATS_MESSAGE_TYPE -> statsConcentrator?.drainAndFlush()

            else -> {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    { UNKNOWN_EVENT_TYPE_PROPERTY_VALUE.format(Locale.US, event["type"]) }
                )
            }
        }
    }

    internal companion object {
        internal const val FLUSH_AND_STOP_STATS_MESSAGE_TYPE = "flush_and_stop_stats"

        internal const val UNSUPPORTED_EVENT_TYPE =
            "Client Stats feature received an event of unsupported type=%s."
        internal const val UNKNOWN_EVENT_TYPE_PROPERTY_VALUE =
            "Client Stats feature received an event with unknown value of \"type\" property=%s."
    }
}
