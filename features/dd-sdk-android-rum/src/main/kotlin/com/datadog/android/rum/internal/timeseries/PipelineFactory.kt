/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.timeseries

import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.domain.InfoProvider
import com.datadog.android.rum.internal.domain.battery.BatteryInfo
import com.datadog.android.rum.internal.domain.display.DisplayInfo
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.internal.timeseries.factory.CpuEventFactory
import com.datadog.android.rum.internal.timeseries.factory.MemoryEventFactory
import com.datadog.android.rum.internal.timeseries.provider.CpuDatapointReader
import com.datadog.android.rum.internal.timeseries.provider.MemoryDatapointReader
import com.datadog.android.rum.internal.vitals.CpuStatReader
import com.datadog.android.rum.internal.vitals.ProcStatusReader
import com.datadog.android.rum.timeseries.TimeseriesConfiguration
import com.datadog.android.rum.timeseries.TimeseriesType

internal class PipelineFactory(
    private val sdkCore: FeatureSdkCore,
    private val dataWriter: DataWriter<Any>,
    private val insightsCollector: InsightsCollector,
    private val configuration: TimeseriesConfiguration,
    private val batteryInfoProvider: InfoProvider<BatteryInfo>,
    private val displayInfoProvider: InfoProvider<DisplayInfo>,
    private val sessionRegistry: CollectedTimeseriesSessionRegistry
) {
    private val typesFactory = mapOf(
        TimeseriesType.CPU to ::createCpuPipeline,
        TimeseriesType.MEMORY to ::createMemoryPipeline
    )

    fun create(sessionType: RumSessionType): List<Pipeline<*>> = configuration.collectTypes.mapNotNull {
        typesFactory[it]?.invoke(sessionType)
    }

    private fun createMemoryPipeline(
        sessionType: RumSessionType
    ) = Pipeline(
        sdkCore = sdkCore,
        reader = MemoryDatapointReader(
            procStatusReader = ProcStatusReader(internalLogger = sdkCore.internalLogger),
            timeProvider = sdkCore.timeProvider,
            intervalMs = configuration.samplingIntervalMs
        ),
        buffer = Buffer(configuration.bufferSize),
        eventFactory = MemoryEventFactory(
            sessionType = sessionType,
            timeProvider = sdkCore.timeProvider,
            batteryInfoProvider = batteryInfoProvider,
            displayInfoProvider = displayInfoProvider,
            internalLogger = sdkCore.internalLogger
        ),
        dataWriter = dataWriter,
        collectedSessionsRegistry = sessionRegistry,
        insightsCollector = insightsCollector
    )

    private fun createCpuPipeline(
        sessionType: RumSessionType
    ) = Pipeline(
        sdkCore = sdkCore,
        reader = CpuDatapointReader(
            cpuStatReader = CpuStatReader(internalLogger = sdkCore.internalLogger),
            timeProvider = sdkCore.timeProvider,
            intervalMs = configuration.samplingIntervalMs
        ),
        buffer = Buffer(configuration.bufferSize),
        eventFactory = CpuEventFactory(
            sessionType = sessionType,
            timeProvider = sdkCore.timeProvider,
            batteryInfoProvider = batteryInfoProvider,
            displayInfoProvider = displayInfoProvider,
            internalLogger = sdkCore.internalLogger
        ),
        dataWriter = dataWriter,
        collectedSessionsRegistry = sessionRegistry,
        insightsCollector = insightsCollector
    )
}
