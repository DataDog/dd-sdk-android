/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.factory

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.core.internal.utils.DdTagsUtils
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.FeaturesContextResolver
import com.datadog.android.rum.internal.domain.InfoProvider
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.battery.BatteryInfo
import com.datadog.android.rum.internal.domain.display.DisplayInfo
import com.datadog.android.rum.internal.domain.scope.resolveSessionReplaySampleRate
import com.datadog.android.rum.internal.domain.scope.resolveTraceSampleRate
import com.datadog.android.rum.internal.domain.scope.toTimeseriesMemoryDevice
import com.datadog.android.rum.internal.domain.scope.toTimeseriesMemoryOs
import com.datadog.android.rum.internal.domain.scope.toTimeseriesMemorySessionPrecondition
import com.datadog.android.rum.internal.domain.scope.tryFromSource
import com.datadog.android.rum.internal.timeseries.DataPoint
import com.datadog.android.rum.internal.toTimeseriesMemorySessionType
import com.datadog.android.rum.model.TimeseriesMemoryEvent
import java.util.UUID

@Suppress("LongParameterList")
internal class MemoryEventFactory(
    private val sessionType: RumSessionType,
    private val totalRamBytes: Long,
    private val timeProvider: TimeProvider,
    private val batteryInfoProvider: InfoProvider<BatteryInfo>,
    private val displayInfoProvider: InfoProvider<DisplayInfo>,
    private val internalLogger: InternalLogger,
    private val featuresContextResolver: FeaturesContextResolver = FeaturesContextResolver()
) : EventFactory<Double, TimeseriesMemoryEvent> {

    override val eventName: String = "memory"

    @Suppress("LongMethod")
    override fun create(
        datadogContext: DatadogContext,
        rumContext: RumContext,
        dataPoints: List<DataPoint<Double>>,
        customAttributes: Map<String, Any?>
    ): TimeseriesMemoryEvent? {
        if (totalRamBytes <= 0L || dataPoints.isEmpty()) return null

        val deviceInfo = datadogContext.deviceInfo
        val syntheticsAttribute = if (
            rumContext.syntheticsTestId.isNullOrBlank() ||
            rumContext.syntheticsResultId.isNullOrBlank()
        ) {
            null
        } else {
            TimeseriesMemoryEvent.Synthetics(
                testId = rumContext.syntheticsTestId,
                resultId = rumContext.syntheticsResultId
            )
        }

        return TimeseriesMemoryEvent(
            dd = TimeseriesMemoryEvent.Dd(
                session = TimeseriesMemoryEvent.DdSession(
                    sessionPrecondition = rumContext.sessionStartReason.toTimeseriesMemorySessionPrecondition()
                ),
                configuration = TimeseriesMemoryEvent.Configuration(
                    sessionSampleRate = rumContext.sessionSampleRate,
                    sessionReplaySampleRate = datadogContext.resolveSessionReplaySampleRate(),
                    traceSampleRate = datadogContext.resolveTraceSampleRate()
                )
            ),
            application = TimeseriesMemoryEvent.Application(
                id = rumContext.applicationId,
                currentLocale = deviceInfo.localeInfo.currentLocale
            ),
            session = TimeseriesMemoryEvent.TimeseriesMemoryEventSession(
                id = rumContext.sessionId,
                type = sessionType.toTimeseriesMemorySessionType(),
                hasReplay = featuresContextResolver.resolveViewHasReplay(
                    datadogContext,
                    rumContext.viewId.orEmpty()
                )
            ),
            synthetics = syntheticsAttribute,
            source = TimeseriesMemoryEvent.Source.tryFromSource(datadogContext.source, internalLogger),
            date = timeProvider.getServerTimestampMillis(),
            version = datadogContext.version,
            service = datadogContext.service,
            buildVersion = datadogContext.versionCode.toString(),
            buildId = datadogContext.appBuildId,
            ddtags = DdTagsUtils.toDdTagsString(datadogContext),
            os = deviceInfo.toTimeseriesMemoryOs(),
            device = deviceInfo.toTimeseriesMemoryDevice(
                batteryInfo = batteryInfoProvider.getState(),
                displayInfo = displayInfoProvider.getState()
            ),
            timeseries = TimeseriesMemoryEvent.Timeseries(
                id = UUID.randomUUID().toString(),
                start = dataPoints.firstOrNull()?.timestampNs ?: 0L,
                end = dataPoints.lastOrNull()?.timestampNs ?: 0L,
                data = TimeseriesMemoryEvent.Data(
                    timestamps = dataPoints.map { it.timestampNs },
                    values = TimeseriesMemoryEvent.Values(
                        memoryPercent = dataPoints.map { it.value / totalRamBytes * PERCENT_FACTOR },
                        memoryFootprint = dataPoints.map { it.value / BYTES_IN_KB }
                    )
                )
            )
        )
    }

    private companion object {
        const val PERCENT_FACTOR = 100.0

        // Must match the factor used in MemoryVitalReader when converting VmRSS kB to bytes.
        // Datadog memory vitals use decimal (SI) units, so 1000 (not 1024) keeps the emitted
        // memory_footprint consistent with the collected value and with memory_percent.
        const val BYTES_IN_KB = 1000
    }
}
