/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.factory

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.core.internal.utils.DdTagsUtils
import com.datadog.android.internal.rum.RumContext
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.FeaturesContextResolver
import com.datadog.android.rum.internal.domain.InfoProvider
import com.datadog.android.rum.internal.domain.battery.BatteryInfo
import com.datadog.android.rum.internal.domain.display.DisplayInfo
import com.datadog.android.rum.internal.domain.scope.resolveSessionReplaySampleRate
import com.datadog.android.rum.internal.domain.scope.resolveTraceSampleRate
import com.datadog.android.rum.internal.domain.scope.toTimeseriesMemoryDevice
import com.datadog.android.rum.internal.domain.scope.toTimeseriesMemoryOs
import com.datadog.android.rum.internal.domain.scope.toTimeseriesMemorySessionPrecondition
import com.datadog.android.rum.internal.domain.scope.tryFromSource
import com.datadog.android.rum.internal.timeseries.DataPoint
import com.datadog.android.rum.internal.timeseries.MemorySnapshot
import com.datadog.android.rum.internal.toTimeseriesMemorySessionType
import com.datadog.android.rum.model.TimeseriesMemoryEvent
import java.util.UUID
import java.util.concurrent.TimeUnit

@Suppress("LongParameterList")
internal class MemoryEventFactory(
    private val sessionType: RumSessionType,
    private val batteryInfoProvider: InfoProvider<BatteryInfo>,
    private val displayInfoProvider: InfoProvider<DisplayInfo>,
    private val internalLogger: InternalLogger,
    private val featuresContextResolver: FeaturesContextResolver = FeaturesContextResolver()
) : EventFactory<MemorySnapshot, TimeseriesMemoryEvent> {

    override val eventName: String = "memory"

    @Suppress("LongMethod")
    override fun create(
        datadogContext: DatadogContext,
        rumContext: RumContext,
        dataPoints: List<DataPoint<MemorySnapshot>>
    ): TimeseriesMemoryEvent? {
        if (dataPoints.isEmpty()) return null

        val deviceInfo = datadogContext.deviceInfo
        val timestampsNs = datadogContext.toServerTimestampsNs(dataPoints)
        val startNs = timestampsNs.firstOrNull() ?: 0L
        val endNs = timestampsNs.lastOrNull() ?: 0L
        val syntheticsTestId = rumContext.syntheticsTestId
        val syntheticsResultId = rumContext.syntheticsResultId
        val syntheticsAttribute = if (
            syntheticsTestId.isNullOrBlank() ||
            syntheticsResultId.isNullOrBlank()
        ) {
            null
        } else {
            TimeseriesMemoryEvent.Synthetics(
                testId = syntheticsTestId,
                resultId = syntheticsResultId
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
                ),
                isMainProcess = datadogContext.processInfo.isMainProcess,
                hasTimeseries = true
            ),
            synthetics = syntheticsAttribute,
            source = TimeseriesMemoryEvent.Source.tryFromSource(datadogContext.source, internalLogger),
            date = TimeUnit.NANOSECONDS.toMillis(startNs),
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
                start = startNs,
                end = endNs,
                data = TimeseriesMemoryEvent.Data(
                    timestamps = timestampsNs,
                    values = TimeseriesMemoryEvent.Values(
                        memoryFootprint = dataPoints.map { it.value.footprintKib },
                        memoryNonResident = dataPoints.map { it.value.nonResidentKib }
                    )
                )
            )
        )
    }
}
