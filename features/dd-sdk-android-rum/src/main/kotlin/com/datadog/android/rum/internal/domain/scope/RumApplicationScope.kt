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
import com.datadog.android.rum.internal.AppStartTimeProvider
import com.datadog.android.rum.internal.DefaultAppStartTimeProvider
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.vitals.VitalMonitor
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
    private val sessionListener: RumSessionListener?,
    private val appStartTimeProvider: AppStartTimeProvider = DefaultAppStartTimeProvider()
) : RumScope, RumViewChangedListener {

    private var rumContext = RumContext(applicationId = applicationId)

    internal val childScopes: MutableList<RumScope> = mutableListOf(
        RumSessionScope(
            this,
            sdkCore,
            sampleRate,
            backgroundTrackingEnabled,
            trackFrustrations,
            this,
            firstPartyHostHeaderTypeResolver,
            cpuVitalMonitor,
            memoryVitalMonitor,
            frameRateVitalMonitor,
            sessionListener,
            false
        )
    )

    val activeSession: RumScope?
        get() {
            return childScopes.find { it.isActive() }
        }

    private var lastActiveViewInfo: RumViewInfo? = null
    private var isSentAppStartedEvent = false

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

        if (!isSentAppStartedEvent) {
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
            this,
            sdkCore,
            sampleRate,
            backgroundTrackingEnabled,
            trackFrustrations,
            this,
            firstPartyHostHeaderTypeResolver,
            cpuVitalMonitor,
            memoryVitalMonitor,
            frameRateVitalMonitor,
            sessionListener,
            true
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
            val processStartTimeNs = appStartTimeProvider.appStartTimeNs
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
            isSentAppStartedEvent = true
        }
    }

    // endregion

    companion object {
        internal const val LAST_ACTIVE_VIEW_GONE_WARNING_MESSAGE = "Attempting to start a new " +
                "session on the last known view (%s) failed because that view has been disposed. "
        internal const val MULTIPLE_ACTIVE_SESSIONS_ERROR = "Application has multiple active " +
                "sessions when starting a new session"
    }
}
