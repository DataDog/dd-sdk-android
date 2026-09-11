/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.internal.profiling.ProfilerEvent
import com.datadog.android.internal.profiling.ProfilingRumContext
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.scope.RumRawEvent
import com.datadog.android.rum.internal.domain.scope.RumVitalAppLaunchEventHelper
import com.datadog.android.rum.internal.profiling.isProfilerRunning
import com.datadog.android.rum.internal.profiling.resolveProfilingQuotaReason
import com.datadog.android.rum.internal.utils.newRumEventWriteOperation
import com.datadog.android.rum.model.VitalAppLaunchEvent
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal interface RumSessionScopeStartupManager {
    fun onAppStartEvent(event: RumRawEvent.AppStartEvent)

    fun onTTIDEvent(
        event: RumRawEvent.AppStartTTIDEvent,
        isSessionTracked: Boolean,
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

    @Suppress("LongMethod")
    override fun onTTIDEvent(
        event: RumRawEvent.AppStartTTIDEvent,
        isSessionTracked: Boolean,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>,
        rumContext: RumContext,
        customAttributes: Map<String, Any?>
    ) {
        if (!isSessionTracked) {
            sdkCore.getFeature(Feature.PROFILING_FEATURE_NAME)?.sendEvent(
                ProfilerEvent.TTIDNotTracked
            )
            return
        }
        ttidReportedForScenario = true

        rumAppStartupTelemetryReporter.reportTTID(
            info = event.info,
            indexInSession = appStartCount - 1
        )

        if (ttidSentForSession) {
            return
        }

        ttidSentForSession = true

        val profilingStatus = datadogContext.getProfilingStatus(rumContext.sessionId)
        val ttidEvent = rumVitalAppLaunchEventHelper.newVitalAppLaunchEvent(
            timestampMs = event.info.scenario.initialTime.timestamp + sdkCore.time.serverTimeOffsetMs,
            datadogContext = datadogContext,
            eventAttributes = emptyMap(),
            customAttributes = customAttributes,
            hasReplay = null,
            rumContext = rumContext,
            durationNs = event.info.durationNs,
            appLaunchMetric = VitalAppLaunchEvent.AppLaunchMetric.TTID,
            scenario = event.info.scenario,
            profilingStatus = profilingStatus,
            // The quota reason is only meaningful when profiling is STOPPED; a running profiler
            // must not carry a denial reason (mirrors the Error/LongTask path in RumViewScope).
            profilingQuotaReason = if (profilingStatus == VitalAppLaunchEvent.ProfilingStatus.STOPPED) {
                datadogContext.resolveProfilingQuotaReason(rumContext.sessionId)
            } else {
                null
            }
        )

        sdkCore.getFeature(Feature.PROFILING_FEATURE_NAME)?.sendEvent(
            // startMs is different for different scenarios. For Cold it will be process start, for Warm
            // it will be Activity.onCreate. But we have application launch profiling working for cold starts only anyway.
            ProfilerEvent.RumVitalEvent(
                rumContext = ProfilingRumContext(
                    applicationId = rumContext.applicationId,
                    sessionId = rumContext.sessionId,
                    viewId = rumContext.viewId,
                    viewName = rumContext.viewName
                ),
                id = ttidEvent.vital.id,
                name = ttidEvent.vital.name,
                type = ProfilerEvent.RumVitalEvent.Type.TTID,
                startMs = ttidEvent.date,
                durationNs = event.info.durationNs
            )
        )

        sendTTIDEvent(
            writeScope = writeScope,
            writer = writer,
            ttidEvent = ttidEvent,
            scenario = event.info.scenario
        )

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
        if (durationNs > MAX_TTFD_DURATION_NS) {
            sdkCore.internalLogger.log(
                level = InternalLogger.Level.WARN,
                targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                messageBuilder = {
                    TTFD_TOO_LARGE_MESSAGE
                },
                throwable = null,
                onlyOnce = false,
                additionalProperties = getTelemetryAttributes(durationNs, scenario)
            )
            return
        }

        val profilingStatus = datadogContext.getProfilingStatus(rumContext.sessionId)
        val ttfdEvent = rumVitalAppLaunchEventHelper.newVitalAppLaunchEvent(
            timestampMs = scenario.initialTime.timestamp + sdkCore.time.serverTimeOffsetMs,
            datadogContext = datadogContext,
            eventAttributes = emptyMap(),
            customAttributes = customAttributes,
            hasReplay = null,
            rumContext = rumContext,
            durationNs = durationNs,
            appLaunchMetric = VitalAppLaunchEvent.AppLaunchMetric.TTFD,
            scenario = scenario,
            profilingStatus = profilingStatus,
            // The quota reason is only meaningful when profiling is STOPPED; a running profiler
            // must not carry a denial reason (mirrors the TTID and Error/LongTask paths).
            profilingQuotaReason = if (profilingStatus == VitalAppLaunchEvent.ProfilingStatus.STOPPED) {
                datadogContext.resolveProfilingQuotaReason(rumContext.sessionId)
            } else {
                null
            }
        )

        sdkCore.getFeature(Feature.PROFILING_FEATURE_NAME)?.sendEvent(
            ProfilerEvent.RumVitalEvent(
                rumContext = ProfilingRumContext(
                    applicationId = rumContext.applicationId,
                    sessionId = rumContext.sessionId,
                    viewId = rumContext.viewId,
                    viewName = rumContext.viewName
                ),
                id = ttfdEvent.vital.id,
                name = ttfdEvent.vital.name,
                type = ProfilerEvent.RumVitalEvent.Type.TTFD,
                startMs = ttfdEvent.date,
                durationNs = durationNs
            )
        )

        sdkCore.newRumEventWriteOperation(writeScope, writer) {
            ttfdEvent
        }.submit()
    }

    private fun sendTTIDEvent(
        writeScope: EventWriteScope,
        writer: DataWriter<Any>,
        ttidEvent: VitalAppLaunchEvent,
        scenario: RumStartupScenario
    ) {
        val durationNs = ttidEvent.vital.duration.toLong()

        if (durationNs > MAX_TTID_DURATION_NS) {
            sdkCore.internalLogger.log(
                level = InternalLogger.Level.WARN,
                targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                messageBuilder = {
                    TTID_TOO_LARGE_MESSAGE
                },
                throwable = null,
                onlyOnce = false,
                additionalProperties = getTelemetryAttributes(durationNs, scenario)
            )
            return
        }

        sdkCore.newRumEventWriteOperation(writeScope, writer) {
            ttidEvent
        }.submit()
    }

    private fun getTelemetryAttributes(
        durationNs: Long,
        scenario: RumStartupScenario
    ): Map<String, Any?> {
        return mapOf(
            RumAppStartupTelemetryReporterImpl.KEY_DURATION_NS to durationNs,
            RumAppStartupTelemetryReporterImpl.KEY_SCENARIO to scenario.name
        )
    }

    private fun DatadogContext.getProfilingStatus(
        currentSessionId: String
    ): VitalAppLaunchEvent.ProfilingStatus? = when {
        isProfilerRunning() -> VitalAppLaunchEvent.ProfilingStatus.RUNNING
        resolveProfilingQuotaReason(currentSessionId) != null -> VitalAppLaunchEvent.ProfilingStatus.STOPPED
        else -> null
    }

    companion object {
        internal const val REPORT_APP_FULLY_DISPLAYED_CALLED_TOO_EARLY_MESSAGE =
            "RumMonitor.reportAppFullyDisplayed was called before the application launch was detected, ignoring it."

        internal const val REPORT_APP_FULLY_DISPLAYED_CALLED_BEFORE_TTID_MESSAGE =
            "RumMonitor.reportAppFullyDisplayed was called before TTID was computed, will report TTID as TTFD."

        internal const val TTID_TOO_LARGE_MESSAGE = "TTID value is too large, skipping it"

        internal const val TTFD_TOO_LARGE_MESSAGE = "TTFD value is too large, skipping it"

        internal val MAX_TTID_DURATION_NS: Long = 1.minutes.inWholeNanoseconds
        internal val MAX_TTFD_DURATION_NS: Long = 90.seconds.inWholeNanoseconds
    }
}
