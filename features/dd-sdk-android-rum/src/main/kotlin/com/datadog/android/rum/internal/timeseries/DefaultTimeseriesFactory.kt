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
import com.datadog.android.rum.internal.RumFeature.Companion.MEMORY_TIMESERIES_DISABLED_MESSAGE
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.internal.timeseries.provider.CpuDatapointReader
import com.datadog.android.rum.internal.timeseries.provider.VitalReaderWrapper
import com.datadog.android.rum.internal.timeseries.serializer.CpuEventSerializer
import com.datadog.android.rum.internal.timeseries.serializer.MemoryEventSerializer
import com.datadog.android.rum.internal.vitals.CpuStatReader
import com.datadog.android.rum.internal.vitals.MemoryVitalReader
import com.datadog.android.rum.timeseries.TimeseriesConfiguration
import java.util.concurrent.ScheduledExecutorService

@Suppress("LongParameterList")
internal class DefaultTimeseriesFactory(
    private val sdkCore: FeatureSdkCore,
    private val configuration: TimeseriesConfiguration,
    private val scheduledExecutorService: ScheduledExecutorService,
    private val insightsCollector: InsightsCollector,
    private val totalRamBytes: Long,
    private val dataWriter: DataWriter<Any>
) : Timeseries.Factory {

    override fun create(
        sessionId: String,
        sessionType: RumSessionType,
        rumContext: RumContext
    ): Timeseries = RumTimeseries(
        internalLogger = sdkCore.internalLogger,
        collectInBackground = configuration.collectInBackground,
        scheduledExecutorService = scheduledExecutorService,
        pipelines = listOfNotNull(
            Pipeline(
                sdkCore = sdkCore,
                reader = CpuDatapointReader(
                    cpuStatReader = CpuStatReader(internalLogger = sdkCore.internalLogger),
                    cpuTimeProvider = sdkCore.timeProvider,
                    intervalMs = configuration.intervalMs
                ),
                buffer = Buffer(configuration.bufferSize),
                serializer = CpuEventSerializer(
                    sessionId = sessionId,
                    sessionType = sessionType,
                    rumContext = rumContext,
                    timeProvider = sdkCore.timeProvider
                ),
                dataWriter = dataWriter,
                insightsCollector = insightsCollector
            ),
            if (totalRamBytes > 0L) {
                Pipeline(
                    sdkCore = sdkCore,
                    reader = VitalReaderWrapper(
                        vitalReader = MemoryVitalReader(internalLogger = sdkCore.internalLogger),
                        timeProvider = sdkCore.timeProvider,
                        intervalMs = configuration.intervalMs
                    ),
                    buffer = Buffer(configuration.bufferSize),
                    serializer = MemoryEventSerializer(
                        sessionId = sessionId,
                        sessionType = sessionType,
                        rumContext = rumContext,
                        totalRamBytes = totalRamBytes,
                        timeProvider = sdkCore.timeProvider
                    ),
                    dataWriter = dataWriter,
                    insightsCollector = insightsCollector
                )
            } else {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    { MEMORY_TIMESERIES_DISABLED_MESSAGE },
                    onlyOnce = true
                )
                null
            }
        )
    )
}
