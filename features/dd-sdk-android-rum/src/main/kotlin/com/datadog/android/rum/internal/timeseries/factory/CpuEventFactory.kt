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
import com.datadog.android.rum.internal.domain.scope.toTimeseriesCpuDevice
import com.datadog.android.rum.internal.domain.scope.toTimeseriesCpuOs
import com.datadog.android.rum.internal.domain.scope.toTimeseriesCpuSessionPrecondition
import com.datadog.android.rum.internal.domain.scope.tryFromSource
import com.datadog.android.rum.internal.timeseries.DataPoint
import com.datadog.android.rum.internal.toTimeseriesCpuSessionType
import com.datadog.android.rum.model.TimeseriesCpuEvent
import java.util.UUID

@Suppress("LongParameterList")
internal class CpuEventFactory(
    private val sessionType: RumSessionType,
    private val timeProvider: TimeProvider,
    private val batteryInfoProvider: InfoProvider<BatteryInfo>,
    private val displayInfoProvider: InfoProvider<DisplayInfo>,
    private val internalLogger: InternalLogger,
    private val featuresContextResolver: FeaturesContextResolver = FeaturesContextResolver()
) : EventFactory<Double, TimeseriesCpuEvent> {

    override val eventName: String = "cpu"

    @Suppress("LongMethod")
    override fun create(
        datadogContext: DatadogContext,
        rumContext: RumContext,
        dataPoints: List<DataPoint<Double>>,
        customAttributes: Map<String, Any?>
    ): TimeseriesCpuEvent? {
        if (dataPoints.isEmpty()) return null

        val deviceInfo = datadogContext.deviceInfo
        val syntheticsAttribute = if (
            rumContext.syntheticsTestId.isNullOrBlank() ||
            rumContext.syntheticsResultId.isNullOrBlank()
        ) {
            null
        } else {
            TimeseriesCpuEvent.Synthetics(
                testId = rumContext.syntheticsTestId,
                resultId = rumContext.syntheticsResultId
            )
        }

        return TimeseriesCpuEvent(
            dd = TimeseriesCpuEvent.Dd(
                session = TimeseriesCpuEvent.DdSession(
                    sessionPrecondition = rumContext.sessionStartReason.toTimeseriesCpuSessionPrecondition()
                ),
                configuration = TimeseriesCpuEvent.Configuration(
                    sessionSampleRate = rumContext.sessionSampleRate,
                    sessionReplaySampleRate = datadogContext.resolveSessionReplaySampleRate(),
                    traceSampleRate = datadogContext.resolveTraceSampleRate()
                )
            ),
            application = TimeseriesCpuEvent.Application(
                id = rumContext.applicationId,
                currentLocale = deviceInfo.localeInfo.currentLocale
            ),
            session = TimeseriesCpuEvent.TimeseriesCpuEventSession(
                id = rumContext.sessionId,
                type = sessionType.toTimeseriesCpuSessionType(),
                hasReplay = featuresContextResolver.resolveViewHasReplay(
                    datadogContext,
                    rumContext.viewId.orEmpty()
                )
            ),
            synthetics = syntheticsAttribute,
            source = TimeseriesCpuEvent.Source.tryFromSource(datadogContext.source, internalLogger),
            date = timeProvider.getServerTimestampMillis(),
            version = datadogContext.version,
            service = datadogContext.service,
            buildVersion = datadogContext.versionCode.toString(),
            buildId = datadogContext.appBuildId,
            ddtags = DdTagsUtils.toDdTagsString(datadogContext),
            os = deviceInfo.toTimeseriesCpuOs(),
            device = deviceInfo.toTimeseriesCpuDevice(
                batteryInfo = batteryInfoProvider.getState(),
                displayInfo = displayInfoProvider.getState()
            ),
            timeseries = TimeseriesCpuEvent.Timeseries(
                id = UUID.randomUUID().toString(),
                start = dataPoints.firstOrNull()?.timestampNs ?: 0L,
                end = dataPoints.lastOrNull()?.timestampNs ?: 0L,
                data = TimeseriesCpuEvent.Data(
                    timestamps = dataPoints.map { it.timestampNs },
                    values = TimeseriesCpuEvent.Values(dataPoints.map { it.value })
                )
            )
        )
    }
}
