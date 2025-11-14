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
import com.datadog.android.rum.model.RumVitalAppLaunchEvent
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
        appLaunchMetric: RumVitalAppLaunchEvent.AppLaunchMetric
    ): RumVitalAppLaunchEvent {
        val syntheticsAttribute = if (
            rumContext.syntheticsTestId.isNullOrBlank() ||
            rumContext.syntheticsResultId.isNullOrBlank()
        ) {
            null
        } else {
            RumVitalAppLaunchEvent.Synthetics(
                testId = rumContext.syntheticsTestId,
                resultId = rumContext.syntheticsResultId
            )
        }

        val sessionType = when {
            rumSessionTypeOverride != null -> rumSessionTypeOverride.toVitalAppLaunch()
            syntheticsAttribute == null -> RumVitalAppLaunchEvent.RumVitalAppLaunchEventSessionType.USER
            else -> RumVitalAppLaunchEvent.RumVitalAppLaunchEventSessionType.SYNTHETICS
        }

        val batteryInfo = batteryInfoProvider.getState()
        val displayInfo = displayInfoProvider.getState()
        val user = datadogContext.userInfo

        val viewId = rumContext.viewId
        val viewUrl = rumContext.viewUrl

        val view = if (viewId != null && viewUrl != null) {
            RumVitalAppLaunchEvent.RumVitalAppLaunchEventView(
                id = viewId,
                referrer = null,
                url = viewUrl,
                name = rumContext.viewName
            )
        } else {
            null
        }

        return RumVitalAppLaunchEvent(
            date = timestampMs,
            context = RumVitalAppLaunchEvent.Context(
                additionalProperties = customAttributes.toMutableMap().also {
                    it.putAll(eventAttributes)
                }
            ),
            dd = RumVitalAppLaunchEvent.Dd(
                session = RumVitalAppLaunchEvent.DdSession(
                    sessionPrecondition = rumContext.sessionStartReason.toVitalAppLaunchSessionPrecondition()
                ),
                configuration = RumVitalAppLaunchEvent.Configuration(sessionSampleRate = sampleRate)
            ),
            application = RumVitalAppLaunchEvent.Application(
                id = rumContext.applicationId,
                currentLocale = datadogContext.deviceInfo.localeInfo.currentLocale
            ),
            synthetics = syntheticsAttribute,
            session = RumVitalAppLaunchEvent.RumVitalAppLaunchEventSession(
                id = rumContext.sessionId,
                type = sessionType,
                hasReplay = hasReplay
            ),
            view = view,
            source = RumVitalAppLaunchEvent.RumVitalAppLaunchEventSource.tryFromSource(
                source = datadogContext.source,
                internalLogger = internalLogger
            ),
            account = datadogContext.accountInfo?.let {
                RumVitalAppLaunchEvent.Account(
                    id = it.id,
                    name = it.name,
                    additionalProperties = it.extraInfo.toMutableMap()
                )
            },
            usr = if (user.hasUserData()) {
                RumVitalAppLaunchEvent.Usr(
                    id = user.id,
                    name = user.name,
                    email = user.email,
                    anonymousId = user.anonymousId,
                    additionalProperties = user.additionalProperties.toMutableMap()
                )
            } else {
                null
            },
            device = RumVitalAppLaunchEvent.Device(
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
            os = RumVitalAppLaunchEvent.Os(
                name = datadogContext.deviceInfo.osName,
                version = datadogContext.deviceInfo.osVersion,
                versionMajor = datadogContext.deviceInfo.osMajorVersion
            ),
            connectivity = datadogContext.networkInfo.toAppLaunchVitalConnectivity(),
            version = datadogContext.version,
            service = datadogContext.service,
            ddtags = buildDDTagsString(datadogContext),
            vital = RumVitalAppLaunchEvent.Vital(
                id = UUID.randomUUID().toString(),
                name = null,
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
