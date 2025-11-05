/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import android.app.ActivityManager
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.rum.DdRumContentProvider
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumSessionListener
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.domain.InfoProvider
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.accessibility.AccessibilitySnapshotManager
import com.datadog.android.rum.internal.domain.battery.BatteryInfo
import com.datadog.android.rum.internal.domain.display.DisplayInfo
import com.datadog.android.rum.internal.metric.SessionMetricDispatcher
import com.datadog.android.rum.internal.metric.slowframes.SlowFramesListener
import com.datadog.android.rum.internal.startup.RumSessionScopeStartupManager
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.metric.interactiontonextview.LastInteractionIdentifier
import com.datadog.android.rum.metric.networksettled.InitialResourceIdentifier
import java.util.concurrent.TimeUnit

@Suppress("LongParameterList")
internal class RumApplicationScope(
    applicationId: String,
    private val sdkCore: InternalSdkCore,
    internal val sampleRate: Float,
    internal val backgroundTrackingEnabled: Boolean,
    internal val trackFrustrations: Boolean,
    private val firstPartyHostHeaderTypeResolver: FirstPartyHostHeaderTypeResolver,
    private val cpuVitalMonitor: VitalMonitor,
    private val memoryVitalMonitor: VitalMonitor,
    private val frameRateVitalMonitor: VitalMonitor,
    private val sessionEndedMetricDispatcher: SessionMetricDispatcher,
    private val sessionListener: RumSessionListener?,
    internal val initialResourceIdentifier: InitialResourceIdentifier,
    internal val lastInteractionIdentifier: LastInteractionIdentifier?,
    private val slowFramesListener: SlowFramesListener?,
    private val rumSessionTypeOverride: RumSessionType?,
    private val accessibilitySnapshotManager: AccessibilitySnapshotManager,
    private val batteryInfoProvider: InfoProvider<BatteryInfo>,
    private val displayInfoProvider: InfoProvider<DisplayInfo>,
    private val rumVitalAppLaunchEventHelper: RumVitalAppLaunchEventHelper,
    private val rumSessionScopeStartupManagerFactory: () -> RumSessionScopeStartupManager
) : RumScope, RumViewChangedListener {

    override val parentScope: RumScope? = null

    private var rumContext = RumContext(applicationId = applicationId)

    internal val childScopes = mutableListOf<RumSessionScope>(
        RumSessionScope(
            parentScope = this,
            sdkCore = sdkCore,
            sessionEndedMetricDispatcher = sessionEndedMetricDispatcher,
            sampleRate = sampleRate,
            backgroundTrackingEnabled = backgroundTrackingEnabled,
            trackFrustrations = trackFrustrations,
            viewChangedListener = this,
            firstPartyHostHeaderTypeResolver = firstPartyHostHeaderTypeResolver,
            cpuVitalMonitor = cpuVitalMonitor,
            memoryVitalMonitor = memoryVitalMonitor,
            frameRateVitalMonitor = frameRateVitalMonitor,
            sessionListener = sessionListener,
            applicationDisplayed = false,
            networkSettledResourceIdentifier = initialResourceIdentifier,
            lastInteractionIdentifier = lastInteractionIdentifier,
            slowFramesListener = slowFramesListener,
            rumSessionTypeOverride = rumSessionTypeOverride,
            accessibilitySnapshotManager = accessibilitySnapshotManager,
            batteryInfoProvider = batteryInfoProvider,
            displayInfoProvider = displayInfoProvider,
            rumVitalAppLaunchEventHelper = rumVitalAppLaunchEventHelper,
            rumSessionScopeStartupManagerFactory = rumSessionScopeStartupManagerFactory
        )
    )

    val activeSession: RumSessionScope?
        get() {
            val activeSessions = childScopes.filter { it.isActive() }
            if (activeSessions.size > 1) {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.MAINTAINER,
                    { MULTIPLE_ACTIVE_SESSIONS_ERROR }
                )
            }
            return activeSessions.lastOrNull()
        }

    private var lastActiveViewInfo: RumViewInfo? = null
    private var isAppStartedEventSent = false

    // region RumScope

    @WorkerThread
    override fun handleEvent(
        event: RumRawEvent,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ): RumScope {
        if (event is RumRawEvent.SetSyntheticsTestAttribute) {
            rumContext = rumContext.copy(
                syntheticsTestId = event.testId,
                syntheticsResultId = event.resultId
            )
        }

        val isInteraction = (event is RumRawEvent.StartView) || (event is RumRawEvent.StartAction)
        if (activeSession == null && isInteraction) {
            startNewSession(event, datadogContext, writeScope, writer)
        }

        if (event !is RumRawEvent.SdkInit && !isAppStartedEventSent) {
            sendApplicationStartEvent(event.eventTime, datadogContext, writeScope, writer)
        }

        delegateToChildren(event, datadogContext, writeScope, writer)

        return this
    }

    override fun isActive(): Boolean {
        return true
    }

    override fun getRumContext(): RumContext {
        return rumContext
    }

    override fun getCustomAttributes(): Map<String, Any?> {
        return GlobalRumMonitor.get(sdkCore).getAttributes()
    }

    // endregion

    // region RumViewChangedListener

    override fun onViewChanged(viewInfo: RumViewInfo) {
        if (viewInfo.isActive) {
            lastActiveViewInfo = viewInfo
        }
    }

    // endregion

    // region Internal

    @WorkerThread
    private fun delegateToChildren(
        event: RumRawEvent,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        val iterator = childScopes.iterator()
        @Suppress("UnsafeThirdPartyFunctionCall") // next/remove can't fail: we checked hasNext
        while (iterator.hasNext()) {
            val result = iterator.next().handleEvent(event, datadogContext, writeScope, writer)
            if (result == null) {
                iterator.remove()
            }
        }
    }

    @WorkerThread
    private fun startNewSession(
        event: RumRawEvent,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        val newSession = RumSessionScope(
            parentScope = this,
            sdkCore = sdkCore,
            sessionEndedMetricDispatcher = sessionEndedMetricDispatcher,
            sampleRate = sampleRate,
            backgroundTrackingEnabled = backgroundTrackingEnabled,
            trackFrustrations = trackFrustrations,
            viewChangedListener = this,
            firstPartyHostHeaderTypeResolver = firstPartyHostHeaderTypeResolver,
            cpuVitalMonitor = cpuVitalMonitor,
            memoryVitalMonitor = memoryVitalMonitor,
            frameRateVitalMonitor = frameRateVitalMonitor,
            sessionListener = sessionListener,
            applicationDisplayed = true,
            networkSettledResourceIdentifier = initialResourceIdentifier,
            lastInteractionIdentifier = lastInteractionIdentifier,
            slowFramesListener = slowFramesListener,
            rumSessionTypeOverride = rumSessionTypeOverride,
            accessibilitySnapshotManager = accessibilitySnapshotManager,
            batteryInfoProvider = batteryInfoProvider,
            displayInfoProvider = displayInfoProvider,
            rumVitalAppLaunchEventHelper = rumVitalAppLaunchEventHelper,
            rumSessionScopeStartupManagerFactory = rumSessionScopeStartupManagerFactory
        )
        childScopes.add(newSession)
        if (event !is RumRawEvent.StartView) {
            lastActiveViewInfo?.let {
                val startViewEvent = RumRawEvent.StartView(
                    key = it.key,
                    attributes = it.attributes
                )
                newSession.handleEvent(startViewEvent, datadogContext, writeScope, writer)
            }
        }

        // Confidence telemetry, only end up with one active session
        if (childScopes.filter { it.isActive() }.size > 1) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.TELEMETRY,
                { MULTIPLE_ACTIVE_SESSIONS_SESSION_START_ERROR }
            )
        }
    }

    @WorkerThread
    private fun sendApplicationStartEvent(
        eventTime: Time,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        val processImportance = DdRumContentProvider.processImportance
        val isForegroundProcess = processImportance ==
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        if (isForegroundProcess) {
            val processStartTimeNs = sdkCore.appStartTimeNs
            // processStartTime is the time in nanoseconds since VM start. To get a timestamp, we want
            // to convert it to milliseconds since epoch provided by System.currentTimeMillis.
            // To do so, we take the offset of those times in the event time, which should be consistent,
            // then add that to our processStartTime to get the correct value.
            val timestampNs = (
                TimeUnit.MILLISECONDS.toNanos(eventTime.timestamp) - eventTime.nanoTime
                ) + processStartTimeNs
            val applicationLaunchViewTime = Time(
                timestamp = TimeUnit.NANOSECONDS.toMillis(timestampNs),
                nanoTime = processStartTimeNs
            )
            val startupTime = eventTime.nanoTime - processStartTimeNs
            val appStartedEvent =
                RumRawEvent.ApplicationStarted(applicationLaunchViewTime, startupTime)
            delegateToChildren(appStartedEvent, datadogContext, writeScope, writer)
            isAppStartedEventSent = true
        }
    }

    // endregion

    companion object {
        internal const val MULTIPLE_ACTIVE_SESSIONS_SESSION_START_ERROR = "Application has multiple active " +
            "sessions when starting a new session"
        internal const val MULTIPLE_ACTIVE_SESSIONS_ERROR = "Application has multiple active " +
            "sessions, this shouldn't happen."
    }
}
