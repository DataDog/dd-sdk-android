/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.domain.InfoProvider
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.battery.BatteryInfo
import com.datadog.android.rum.internal.domain.display.DisplayInfo
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.internal.timeseries.factory.CpuEventFactory
import com.datadog.android.rum.internal.timeseries.factory.MemoryEventFactory
import com.datadog.android.rum.internal.timeseries.provider.CpuDatapointReader
import com.datadog.android.rum.internal.timeseries.provider.VitalReaderWrapper
import com.datadog.android.rum.internal.vitals.CpuStatReader
import com.datadog.android.rum.internal.vitals.MemoryVitalReader
import com.datadog.android.rum.timeseries.TimeseriesConfiguration
import com.datadog.android.rum.timeseries.TimeseriesType
import java.util.concurrent.ScheduledExecutorService

@Suppress("LongParameterList")
internal class DefaultTimeseriesCollectorFactory(
    private val sdkCore: FeatureSdkCore,
    private val configuration: TimeseriesConfiguration,
    private val scheduledExecutorService: ScheduledExecutorService,
    private val insightsCollector: InsightsCollector,
    private val totalRamBytes: Long,
    private val dataWriter: DataWriter<Any>,
    private val batteryInfoProvider: InfoProvider<BatteryInfo>,
    private val displayInfoProvider: InfoProvider<DisplayInfo>
) : TimeseriesCollector.Factory {

    override fun create(
        sessionType: RumSessionType,
        rumContext: RumContext
    ): TimeseriesCollector {
        val pipelines = mutableListOf<Pipeline<*>>()

        if (TimeseriesType.CPU in configuration.enabledTypes) {
            pipelines += createCpuPipeline(sessionType)
        }

        if (TimeseriesType.MEMORY in configuration.enabledTypes) {
            if (totalRamBytes > 0L) {
                pipelines += createMemoryPipeline(sessionType)
            } else {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    { MEMORY_TIMESERIES_DISABLED_MESSAGE },
                    onlyOnce = true
                )
            }
        }

        return DefaultTimeseriesCollector(
            internalLogger = sdkCore.internalLogger,
            collectInBackground = configuration.collectInBackground,
            scheduledExecutorService = scheduledExecutorService,
            rumContext = rumContext,
            pipelines = pipelines
        )
    }

    private fun createMemoryPipeline(sessionType: RumSessionType) = Pipeline(
        sdkCore = sdkCore,
        reader = VitalReaderWrapper(
            vitalReader = MemoryVitalReader(internalLogger = sdkCore.internalLogger),
            timeProvider = sdkCore.timeProvider,
            intervalMs = TimeseriesConfiguration.DEFAULT_INTERVAL_MS
        ),
        buffer = Buffer(TimeseriesConfiguration.DEFAULT_BUFFER_SIZE),
        eventFactory = MemoryEventFactory(
            sessionType = sessionType,
            totalRamBytes = totalRamBytes,
            timeProvider = sdkCore.timeProvider,
            batteryInfoProvider = batteryInfoProvider,
            displayInfoProvider = displayInfoProvider,
            internalLogger = sdkCore.internalLogger
        ),
        dataWriter = dataWriter,
        insightsCollector = insightsCollector
    )

    private fun createCpuPipeline(sessionType: RumSessionType) = Pipeline(
        sdkCore = sdkCore,
        reader = CpuDatapointReader(
            cpuStatReader = CpuStatReader(internalLogger = sdkCore.internalLogger),
            timeProvider = sdkCore.timeProvider,
            intervalMs = TimeseriesConfiguration.DEFAULT_INTERVAL_MS
        ),
        buffer = Buffer(TimeseriesConfiguration.DEFAULT_BUFFER_SIZE),
        eventFactory = CpuEventFactory(
            sessionType = sessionType,
            timeProvider = sdkCore.timeProvider,
            batteryInfoProvider = batteryInfoProvider,
            displayInfoProvider = displayInfoProvider,
            internalLogger = sdkCore.internalLogger
        ),
        dataWriter = dataWriter,
        insightsCollector = insightsCollector
    )

    internal companion object {
        const val MEMORY_TIMESERIES_DISABLED_MESSAGE =
            "Unable to read total device memory; memory timeseries collection is disabled."
    }
}
