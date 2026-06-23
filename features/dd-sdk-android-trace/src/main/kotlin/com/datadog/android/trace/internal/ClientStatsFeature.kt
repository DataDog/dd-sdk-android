/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal

import android.content.Context
import com.datadog.android.api.InternalLogger
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

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

    private val timeProvider = sdkCore.timeProvider
    private val statAggregator: ExecutorService by lazy {
        sdkCore.createSingleThreadExecutorService("client-side-stats-aggregator")
    }
    private val flushScheduler: ScheduledExecutorService by lazy {
        sdkCore.createScheduledExecutorService("client-side-flush-scheduler")
    }
    private val statsConcentrator by lazy {
        // runtimeId is static and encapsulated in Tracer core's Config. There is no way to get it except via the root
        // span's tags or this Config object
        val runtimeID = Config.get().runtimeId

        StatsConcentrator(
            sdkCore,
            ddSpanToSpanEventMapper = CoreTracerSpanToSpanEventMapper(networkInfoEnabled),
            eventMapper = SpanEventMapperWrapper(spanEventMapper, sdkCore.internalLogger),
            statAggregator,
            BatchStatsWriter(sdkCore, runtimeID),
            timeProvider
        )
    }

    internal val aggregator by lazy {
        ClientStatsAdapter(statsConcentrator)
    }

    override val requestFactory = ClientStatsRequestFactory(customEndpointUrl)

    override fun onInitialize(appContext: Context) {
        startPeriodicFlush()
    }

    private fun startPeriodicFlush() {
        try {
            @Suppress("UnsafeThirdPartyFunctionCall") // exception caught below
            flushScheduler.scheduleWithFixedDelay(
                { triggerFlush(flushAll = false) },
                FLUSH_INTERVAL.inWholeMilliseconds,
                FLUSH_INTERVAL.inWholeMilliseconds,
                TimeUnit.MILLISECONDS
            )
        } catch (e: RejectedExecutionException) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { "Failed to schedule stats flush" },
                e
            )
        }
    }

    override fun onStop() {
        flushScheduler.shutdownNow()

        triggerFlush(flushAll = true)

        statAggregator.shutdown()
        try {
            @Suppress("UnsafeThirdPartyFunctionCall") // InterruptedException is caught and handled
            if (!statAggregator.awaitTermination(DRAIN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                statAggregator.shutdownNow()
            }
        } catch (_: InterruptedException) {
            statAggregator.shutdownNow()
            @Suppress("UnsafeThirdPartyFunctionCall") // safe - SecurityException not thrown in Android
            Thread.currentThread().interrupt()
        }
    }

    private fun triggerFlush(flushAll: Boolean) {
        statsConcentrator.scheduleFlush(flushAll)
    }

    private companion object {
        private const val DRAIN_WAIT_SECONDS = 10L
        private val FLUSH_INTERVAL = 10.seconds
    }
}
