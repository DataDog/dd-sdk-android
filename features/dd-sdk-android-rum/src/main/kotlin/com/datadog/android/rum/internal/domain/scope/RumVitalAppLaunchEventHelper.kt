/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.domain.InfoProvider
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.battery.BatteryInfo
import com.datadog.android.rum.internal.domain.display.DisplayInfo
import com.datadog.android.rum.internal.startup.RumStartupScenario
import com.datadog.android.rum.internal.toVitalAppLaunch
import com.datadog.android.rum.internal.utils.buildDDTagsString
import com.datadog.android.rum.internal.utils.hasUserData
import com.datadog.android.rum.model.VitalAppLaunchEvent
import java.util.UUID

internal class RumVitalAppLaunchEventHelper(
    private val rumSessionTypeOverride: RumSessionType?,
    private val batteryInfoProvider: InfoProvider<BatteryInfo>,
    private val displayInfoProvider: InfoProvider<DisplayInfo>,
    private val sampleRate: Float,
    private val internalLogger: InternalLogger
) {
    @Suppress("LongMethod")
    fun newVitalAppLaunchEvent(
        timestampMs: Long,
        datadogContext: DatadogContext,
        eventAttributes: Map<String, Any?>,
        customAttributes: Map<String, Any?>,
        hasReplay: Boolean?,
        rumContext: RumContext,
        durationNs: Long,
        scenario: RumStartupScenario,
        appLaunchMetric: VitalAppLaunchEvent.AppLaunchMetric,
        profilingStatus: VitalAppLaunchEvent.ProfilingStatus?
    ): VitalAppLaunchEvent {
        val syntheticsAttribute = if (
            rumContext.syntheticsTestId.isNullOrBlank() ||
            rumContext.syntheticsResultId.isNullOrBlank()
        ) {
            null
        } else {
            VitalAppLaunchEvent.Synthetics(
                testId = rumContext.syntheticsTestId,
                resultId = rumContext.syntheticsResultId
            )
        }

        val sessionType = when {
            rumSessionTypeOverride != null -> rumSessionTypeOverride.toVitalAppLaunch()
            syntheticsAttribute == null -> VitalAppLaunchEvent.VitalAppLaunchEventSessionType.USER
            else -> VitalAppLaunchEvent.VitalAppLaunchEventSessionType.SYNTHETICS
        }

        val batteryInfo = batteryInfoProvider.getState()
        val displayInfo = displayInfoProvider.getState()
        val user = datadogContext.userInfo

        val viewId = rumContext.viewId
        val viewUrl = rumContext.viewUrl

        val view = if (viewId != null && viewUrl != null) {
            VitalAppLaunchEvent.VitalAppLaunchEventView(
                id = viewId,
                referrer = null,
                url = viewUrl,
                name = rumContext.viewName
            )
        } else {
            null
        }

        return VitalAppLaunchEvent(
            date = timestampMs,
            context = VitalAppLaunchEvent.Context(
                additionalProperties = customAttributes.toMutableMap().also {
                    it.putAll(eventAttributes)
                }
            ),
            dd = VitalAppLaunchEvent.Dd(
                session = VitalAppLaunchEvent.DdSession(
                    sessionPrecondition = rumContext.sessionStartReason.toVitalAppLaunchSessionPrecondition()
                ),
                configuration = VitalAppLaunchEvent.Configuration(sessionSampleRate = sampleRate),
                profiling = VitalAppLaunchEvent.Profiling(
                    status = profilingStatus
                )
            ),
            application = VitalAppLaunchEvent.Application(
                id = rumContext.applicationId,
                currentLocale = datadogContext.deviceInfo.localeInfo.currentLocale
            ),
            synthetics = syntheticsAttribute,
            session = VitalAppLaunchEvent.VitalAppLaunchEventSession(
                id = rumContext.sessionId,
                type = sessionType,
                hasReplay = hasReplay
            ),
            view = view,
            source = VitalAppLaunchEvent.VitalAppLaunchEventSource.tryFromSource(
                source = datadogContext.source,
                internalLogger = internalLogger
            ),
            account = datadogContext.accountInfo?.let {
                VitalAppLaunchEvent.Account(
                    id = it.id,
                    name = it.name,
                    additionalProperties = it.extraInfo.toMutableMap()
                )
            },
            usr = if (user.hasUserData()) {
                VitalAppLaunchEvent.Usr(
                    id = user.id,
                    name = user.name,
                    email = user.email,
                    anonymousId = user.anonymousId,
                    additionalProperties = user.additionalProperties.toMutableMap()
                )
            } else {
                null
            },
            device = VitalAppLaunchEvent.Device(
                type = datadogContext.deviceInfo.deviceType.toVitalAppLaunchSchemaType(),
                name = datadogContext.deviceInfo.deviceName,
                model = datadogContext.deviceInfo.deviceModel,
                brand = datadogContext.deviceInfo.deviceBrand,
                architecture = datadogContext.deviceInfo.architecture,
                locales = datadogContext.deviceInfo.localeInfo.locales,
                timeZone = datadogContext.deviceInfo.localeInfo.timeZone,
                batteryLevel = batteryInfo.batteryLevel,
                powerSavingMode = batteryInfo.lowPowerMode,
                brightnessLevel = displayInfo.screenBrightness
            ),
            os = VitalAppLaunchEvent.Os(
                name = datadogContext.deviceInfo.osName,
                version = datadogContext.deviceInfo.osVersion,
                versionMajor = datadogContext.deviceInfo.osMajorVersion
            ),
            connectivity = datadogContext.networkInfo.toAppLaunchVitalConnectivity(),
            version = datadogContext.version,
            service = datadogContext.service,
            ddtags = buildDDTagsString(datadogContext),
            vital = VitalAppLaunchEvent.Vital(
                id = UUID.randomUUID().toString(),
                name = appLaunchMetric.vitalName(),
                description = null,
                appLaunchMetric = appLaunchMetric,
                duration = durationNs,
                startupType = scenario.toVitalAppLaunchStartupType(),
                isPrewarmed = null,
                hasSavedInstanceStateBundle = scenario.hasSavedInstanceStateBundle
            )
        )
    }
}

private fun VitalAppLaunchEvent.AppLaunchMetric.vitalName(): String {
    return when (this) {
        VitalAppLaunchEvent.AppLaunchMetric.TTID -> "time_to_initial_display"
        VitalAppLaunchEvent.AppLaunchMetric.TTFD -> "time_to_full_display"
    }
}
