/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.scope.RumRawEvent
import com.datadog.android.rum.internal.domain.scope.RumVitalAppLaunchEventHelper
import com.datadog.android.rum.internal.utils.newRumEventWriteOperation
import com.datadog.android.rum.model.RumVitalAppLaunchEvent

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
            rumVitalAppLaunchEventHelper: RumVitalAppLaunchEventHelper,
            sdkCore: InternalSdkCore,
            rumAppStartupTelemetryReporter: RumAppStartupTelemetryReporter
        ): RumSessionScopeStartupManager {
            return RumSessionScopeStartupManagerImpl(
                rumVitalAppLaunchEventHelper = rumVitalAppLaunchEventHelper,
                sdkCore = sdkCore,
                rumAppStartupTelemetryReporter
            )
        }
    }
}

internal class RumSessionScopeStartupManagerImpl(
    private val rumVitalAppLaunchEventHelper: RumVitalAppLaunchEventHelper,
    private val sdkCore: InternalSdkCore,
    private val rumAppStartupTelemetryReporter: RumAppStartupTelemetryReporter
) : RumSessionScopeStartupManager {

    private var lastScenario: RumStartupScenario? = null

    private var ttfdReportedForScenario: Boolean = false
    private var ttidReportedForScenario: Boolean = false

    private var ttfdReportedForSession = false
    private var ttidSentForSession = false

    private var appStartCount = 0

    override fun onAppStartEvent(event: RumRawEvent.AppStartEvent) {
        lastScenario = event.scenario

        ttfdReportedForScenario = false
        ttidReportedForScenario = false

        appStartCount++
    }

    override fun onTTIDEvent(
        event: RumRawEvent.AppStartTTIDEvent,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>,
        rumContext: RumContext,
        customAttributes: Map<String, Any?>
    ) {
        ttidReportedForScenario = true

        rumAppStartupTelemetryReporter.reportTTID(
            info = event.info,
            indexInSession = appStartCount - 1
        )

        if (ttidSentForSession) {
            return
        }

        ttidSentForSession = true

        sdkCore.newRumEventWriteOperation(datadogContext, writeScope, writer) {
            rumVitalAppLaunchEventHelper.newVitalAppLaunchEvent(
                timestampMs = event.info.scenario.initialTime.timestamp + sdkCore.time.serverTimeOffsetMs,
                datadogContext = datadogContext,
                eventAttributes = emptyMap(),
                customAttributes = customAttributes,
                hasReplay = null,
                rumContext = rumContext,
                durationNs = event.info.durationNs,
                appLaunchMetric = RumVitalAppLaunchEvent.AppLaunchMetric.TTID,
                scenario = event.info.scenario
            )
        }.submit()

        if (ttfdReportedForScenario) {
            /**
             * [com.datadog.android.rum.RumMonitor.reportAppFullyDisplayed] was called earlier than TTID
             * was computed, reporting TTID value also as TTFD.
             */
            sendTTFDEvent(
                datadogContext = datadogContext,
                writeScope = writeScope,
                writer = writer,
                rumContext = rumContext,
                customAttributes = customAttributes,
                durationNs = event.info.durationNs,
                scenario = event.info.scenario
            )
        }
    }

    @Suppress("ReturnCount")
    override fun onTTFDEvent(
        event: RumRawEvent.AppStartTTFDEvent,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>,
        rumContext: RumContext,
        customAttributes: Map<String, Any?>
    ) {
        if (ttfdReportedForSession) {
            return
        }

        ttfdReportedForSession = true

        val scenario = lastScenario

        if (scenario == null) {
            sdkCore.internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.USER,
                messageBuilder = {
                    REPORT_APP_FULLY_DISPLAYED_CALLED_TOO_EARLY_MESSAGE
                },
                throwable = null,
                onlyOnce = false,
                additionalProperties = null
            )
            return
        }

        ttfdReportedForScenario = true

        if (!ttidReportedForScenario) {
            sdkCore.internalLogger.log(
                level = InternalLogger.Level.WARN,
                target = InternalLogger.Target.USER,
                messageBuilder = {
                    REPORT_APP_FULLY_DISPLAYED_CALLED_BEFORE_TTID_MESSAGE
                },
                throwable = null,
                onlyOnce = false,
                additionalProperties = null
            )
            return
        }

        sendTTFDEvent(
            datadogContext = datadogContext,
            writeScope = writeScope,
            writer = writer,
            rumContext = rumContext,
            customAttributes = customAttributes,
            durationNs = event.eventTime.nanoTime - scenario.initialTime.nanoTime,
            scenario = scenario
        )
    }

    private fun sendTTFDEvent(
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>,
        rumContext: RumContext,
        customAttributes: Map<String, Any?>,
        durationNs: Long,
        scenario: RumStartupScenario
    ) {
        sdkCore.newRumEventWriteOperation(datadogContext, writeScope, writer) {
            rumVitalAppLaunchEventHelper.newVitalAppLaunchEvent(
                timestampMs = scenario.initialTime.timestamp + sdkCore.time.serverTimeOffsetMs,
                datadogContext = datadogContext,
                eventAttributes = emptyMap(),
                customAttributes = customAttributes,
                hasReplay = null,
                rumContext = rumContext,
                durationNs = durationNs,
                appLaunchMetric = RumVitalAppLaunchEvent.AppLaunchMetric.TTFD,
                scenario = scenario
            )
        }.submit()
    }

    companion object {
        internal const val REPORT_APP_FULLY_DISPLAYED_CALLED_TOO_EARLY_MESSAGE =
            "RumMonitor.reportAppFullyDisplayed was called before the application launch was detected, ignoring it."

        internal const val REPORT_APP_FULLY_DISPLAYED_CALLED_BEFORE_TTID_MESSAGE =
            "RumMonitor.reportAppFullyDisplayed was called before TTID was computed, will report TTID as TTFD."
    }
}
