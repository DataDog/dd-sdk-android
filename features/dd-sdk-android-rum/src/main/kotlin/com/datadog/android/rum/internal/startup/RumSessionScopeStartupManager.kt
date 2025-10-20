/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.scope.RumRawEvent
import com.datadog.android.rum.internal.domain.scope.RumVitalEventHelper
import com.datadog.android.rum.internal.domain.scope.toVitalStartupType
import com.datadog.android.rum.internal.utils.newRumEventWriteOperation
import com.datadog.android.rum.model.VitalEvent
import java.util.UUID

internal interface RumSessionScopeStartupManager {
    fun onAppStartEvent(event: RumRawEvent.AppStartEvent)

    fun onTTIDEvent(
        event: RumRawEvent.AppStartTTIDEvent,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>,
        rumContext: RumContext,
        customAttributes: Map<String, Any?>
    )

    fun onTTFDEvent(
        event: RumRawEvent.AppStartTTFDEvent,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>,
        rumContext: RumContext,
        customAttributes: Map<String, Any?>
    )

    companion object {
        fun create(
            rumVitalEventHelper: RumVitalEventHelper,
            sdkCore: InternalSdkCore,
            rumAppStartupTelemetryReporter: RumAppStartupTelemetryReporter
        ): RumSessionScopeStartupManager {
            return RumSessionScopeStartupManagerImpl(
                rumVitalEventHelper = rumVitalEventHelper,
                sdkCore = sdkCore,
                rumAppStartupTelemetryReporter
            )
        }
    }
}

internal class RumSessionScopeStartupManagerImpl(
    private val rumVitalEventHelper: RumVitalEventHelper,
    private val sdkCore: InternalSdkCore,
    private val rumAppStartupTelemetryReporter: RumAppStartupTelemetryReporter
) : RumSessionScopeStartupManager {

    private var lastScenario: RumStartupScenario? = null

    private var ttfdSentForSession = false
    private var ttidSentForSession = false

    private var appStartIndex = 0

    override fun onAppStartEvent(event: RumRawEvent.AppStartEvent) {
        lastScenario = event.scenario
        appStartIndex++
    }

    override fun onTTIDEvent(
        event: RumRawEvent.AppStartTTIDEvent,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>,
        rumContext: RumContext,
        customAttributes: Map<String, Any?>
    ) {
        rumAppStartupTelemetryReporter.reportTTID(
            info = event.info,
            indexInSession = appStartIndex - 1
        )

        if (ttidSentForSession) {
            return
        }

        ttidSentForSession = true

        sdkCore.newRumEventWriteOperation(datadogContext, writeScope, writer) {
            rumVitalEventHelper.newVitalEvent(
                timestampMs = event.info.scenario.initialTime.timestamp + sdkCore.time.serverTimeOffsetMs,
                datadogContext = datadogContext,
                eventAttributes = emptyMap(),
                customAttributes = customAttributes,
                view = null,
                hasReplay = null,
                rumContext = rumContext,
                vital = VitalEvent.Vital.AppLaunchProperties(
                    id = UUID.randomUUID().toString(),
                    name = null,
                    description = null,
                    appLaunchMetric = VitalEvent.AppLaunchMetric.TTID,
                    duration = event.info.durationNs,
                    startupType = event.info.scenario.toVitalStartupType(),
                    isPrewarmed = null,
                    hasSavedInstanceStateBundle = event.info.scenario.hasSavedInstanceStateBundle
                )
            )
        }.submit()
    }

    override fun onTTFDEvent(
        event: RumRawEvent.AppStartTTFDEvent,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>,
        rumContext: RumContext,
        customAttributes: Map<String, Any?>
    ) {
        if (ttfdSentForSession) {
            return
        }

        ttfdSentForSession = true

        val scenario = lastScenario ?: return

        sdkCore.newRumEventWriteOperation(datadogContext, writeScope, writer) {
            rumVitalEventHelper.newVitalEvent(
                timestampMs = scenario.initialTime.timestamp + sdkCore.time.serverTimeOffsetMs,
                datadogContext = datadogContext,
                eventAttributes = emptyMap(),
                customAttributes = customAttributes,
                view = null,
                hasReplay = null,
                rumContext = rumContext,
                vital = VitalEvent.Vital.AppLaunchProperties(
                    id = UUID.randomUUID().toString(),
                    name = null,
                    description = null,
                    appLaunchMetric = VitalEvent.AppLaunchMetric.TTFD,
                    duration = event.eventTime.nanoTime - scenario.initialTime.nanoTime,
                    startupType = scenario.toVitalStartupType(),
                    isPrewarmed = null,
                    hasSavedInstanceStateBundle = scenario.hasSavedInstanceStateBundle
                )
            )
        }.submit()
    }
}
