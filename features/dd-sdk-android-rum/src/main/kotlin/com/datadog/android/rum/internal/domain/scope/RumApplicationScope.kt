/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import android.app.ActivityManager
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.rum.DdRumContentProvider
import com.datadog.android.rum.RumSessionListener
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.accessibility.AccessibilitySnapshotManager
import com.datadog.android.rum.internal.metric.SessionMetricDispatcher
import com.datadog.android.rum.internal.metric.slowframes.SlowFramesListener
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
    private val accessibilitySnapshotManager: AccessibilitySnapshotManager
) : RumScope, RumViewChangedListener {

    private var rumContext = RumContext(applicationId = applicationId)

    internal val childScopes: MutableList<RumScope> = mutableListOf(
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
            accessibilitySnapshotManager = accessibilitySnapshotManager
        )
    )

    val activeSession: RumScope?
        get() {
            return childScopes.find { it.isActive() }
        }

    private var lastActiveViewInfo: RumViewInfo? = null
    private var isAppStartedEventSent = false

    // region RumScope

    @WorkerThread
    override fun handleEvent(
        event: RumRawEvent,
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
            startNewSession(event, writer)
        } else if (event is RumRawEvent.StopSession) {
            sdkCore.updateFeatureContext(Feature.RUM_FEATURE_NAME) {
                it.putAll(getRumContext().toMap())
            }
        }

        if (event !is RumRawEvent.SdkInit && !isAppStartedEventSent) {
            sendApplicationStartEvent(event.eventTime, writer)
        }

        delegateToChildren(event, writer)

        return this
    }

    override fun isActive(): Boolean {
        return true
    }

    override fun getRumContext(): RumContext {
        return rumContext
    }

    // endregion

    override fun onViewChanged(viewInfo: RumViewInfo) {
        if (viewInfo.isActive) {
            lastActiveViewInfo = viewInfo
        }
    }

    // region Internal

    @WorkerThread
    private fun delegateToChildren(
        event: RumRawEvent,
        writer: DataWriter<Any>
    ) {
        val iterator = childScopes.iterator()
        @Suppress("UnsafeThirdPartyFunctionCall") // next/remove can't fail: we checked hasNext
        while (iterator.hasNext()) {
            val result = iterator.next().handleEvent(event, writer)
            if (result == null) {
                iterator.remove()
            }
        }
    }

    @WorkerThread
    private fun startNewSession(event: RumRawEvent, writer: DataWriter<Any>) {
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
            accessibilitySnapshotManager = accessibilitySnapshotManager
        )
        childScopes.add(newSession)
        if (event !is RumRawEvent.StartView) {
            lastActiveViewInfo?.let {
                val startViewEvent = RumRawEvent.StartView(
                    key = it.key,
                    attributes = it.attributes
                )
                newSession.handleEvent(startViewEvent, writer)
            }
        }

        // Confidence telemetry, only end up with one active session
        if (childScopes.filter { it.isActive() }.size > 1) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.TELEMETRY,
                { MULTIPLE_ACTIVE_SESSIONS_ERROR }
            )
        }
    }

    @WorkerThread
    private fun sendApplicationStartEvent(eventTime: Time, writer: DataWriter<Any>) {
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
            delegateToChildren(appStartedEvent, writer)
            isAppStartedEventSent = true
        }
    }

    // endregion

    companion object {
        internal const val MULTIPLE_ACTIVE_SESSIONS_ERROR = "Application has multiple active " +
            "sessions when starting a new session"
    }
}
