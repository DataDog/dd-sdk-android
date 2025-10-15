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
import com.datadog.android.rum.internal.toVital
import com.datadog.android.rum.internal.utils.buildDDTagsString
import com.datadog.android.rum.internal.utils.hasUserData
import com.datadog.android.rum.model.VitalEvent

internal class RumVitalEventHelper(
    private val rumSessionTypeOverride: RumSessionType?,
    private val batteryInfoProvider: InfoProvider<BatteryInfo>,
    private val displayInfoProvider: InfoProvider<DisplayInfo>,
    private val sampleRate: Float,
    private val internalLogger: InternalLogger
) {
    @Suppress("LongMethod")
    fun newVitalEvent(
        timestampMs: Long,
        datadogContext: DatadogContext,
        eventAttributes: Map<String, Any?>,
        customAttributes: Map<String, Any?>,
        view: VitalEvent.VitalEventView?,
        hasReplay: Boolean?,
        rumContext: RumContext,
        vital: VitalEvent.Vital
    ): VitalEvent {
        val syntheticsAttribute = if (
            rumContext.syntheticsTestId.isNullOrBlank() ||
            rumContext.syntheticsResultId.isNullOrBlank()
        ) {
            null
        } else {
            VitalEvent.Synthetics(
                testId = rumContext.syntheticsTestId,
                resultId = rumContext.syntheticsResultId
            )
        }

        val sessionType = when {
            rumSessionTypeOverride != null -> rumSessionTypeOverride.toVital()
            syntheticsAttribute == null -> VitalEvent.VitalEventSessionType.USER
            else -> VitalEvent.VitalEventSessionType.SYNTHETICS
        }

        val batteryInfo = batteryInfoProvider.getState()
        val displayInfo = displayInfoProvider.getState()
        val user = datadogContext.userInfo

        return VitalEvent(
            date = timestampMs,
            context = VitalEvent.Context(
                additionalProperties = customAttributes.toMutableMap().also {
                    it.putAll(eventAttributes)
                }
            ),
            dd = VitalEvent.Dd(
                session = VitalEvent.DdSession(
                    sessionPrecondition = rumContext.sessionStartReason.toVitalSessionPrecondition()
                ),
                configuration = VitalEvent.Configuration(sessionSampleRate = sampleRate)
            ),
            application = VitalEvent.Application(
                id = rumContext.applicationId,
                currentLocale = datadogContext.deviceInfo.localeInfo.currentLocale
            ),
            synthetics = syntheticsAttribute,
            session = VitalEvent.VitalEventSession(
                id = rumContext.sessionId,
                type = sessionType,
                hasReplay = hasReplay
            ),
            view = view,
            source = VitalEvent.VitalEventSource.tryFromSource(
                source = datadogContext.source,
                internalLogger = internalLogger
            ),
            account = datadogContext.accountInfo?.let {
                VitalEvent.Account(
                    id = it.id,
                    name = it.name,
                    additionalProperties = it.extraInfo.toMutableMap()
                )
            },
            usr = if (user.hasUserData()) {
                VitalEvent.Usr(
                    id = user.id,
                    name = user.name,
                    email = user.email,
                    anonymousId = user.anonymousId,
                    additionalProperties = user.additionalProperties.toMutableMap()
                )
            } else {
                null
            },
            device = VitalEvent.Device(
                type = datadogContext.deviceInfo.deviceType.toVitalSchemaType(),
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
            os = VitalEvent.Os(
                name = datadogContext.deviceInfo.osName,
                version = datadogContext.deviceInfo.osVersion,
                versionMajor = datadogContext.deviceInfo.osMajorVersion
            ),
            connectivity = datadogContext.networkInfo.toVitalConnectivity(),
            version = datadogContext.version,
            service = datadogContext.service,
            ddtags = buildDDTagsString(datadogContext),
            vital = vital
        )
    }
}
