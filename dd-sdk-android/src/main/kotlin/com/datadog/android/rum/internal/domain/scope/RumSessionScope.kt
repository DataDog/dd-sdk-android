/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import android.annotation.SuppressLint
import android.app.ActivityManager.RunningAppProcessInfo
import android.os.Build
import android.os.Process
import android.os.SystemClock
import com.datadog.android.Datadog
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.net.FirstPartyHostDetector
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.persistence.NoOpDataWriter
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import com.datadog.android.core.internal.system.DefaultBuildSdkVersionProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumSessionListener
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.event.RumEventSourceProvider
import com.datadog.android.rum.internal.vitals.NoOpVitalMonitor
import com.datadog.android.rum.internal.vitals.VitalMonitor
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

internal class RumSessionScope(
    private val parentScope: RumScope,
    internal val samplingRate: Float,
    internal val backgroundTrackingEnabled: Boolean,
    internal val firstPartyHostDetector: FirstPartyHostDetector,
    private val cpuVitalMonitor: VitalMonitor,
    private val memoryVitalMonitor: VitalMonitor,
    private val frameRateVitalMonitor: VitalMonitor,
    private val timeProvider: TimeProvider,
    internal val sessionListener: RumSessionListener?,
    private val rumEventSourceProvider: RumEventSourceProvider,
    private val buildSdkVersionProvider: BuildSdkVersionProvider = DefaultBuildSdkVersionProvider(),
    private val sessionInactivityNanos: Long = DEFAULT_SESSION_INACTIVITY_NS,
    private val sessionMaxDurationNanos: Long = DEFAULT_SESSION_MAX_DURATION_NS
) : RumScope {

    internal val childrenScopes = mutableListOf<RumScope>()

    internal var keepSession: Boolean = false
    internal var sessionId = RumContext.NULL_UUID
    internal val sessionStartNs = AtomicLong(System.nanoTime())
    internal val lastUserInteractionNs = AtomicLong(0L)

    private var resetSessionTime: Long? = null

    internal var applicationDisplayed: Boolean = false

    private val random = SecureRandom()
    private val noOpWriter = NoOpDataWriter<Any>()

    init {
        GlobalRum.updateRumContext(getRumContext())
    }

    // region RumScope

    override fun handleEvent(
        event: RumRawEvent,
        writer: DataWriter<Any>
    ): RumScope {
        if (event is RumRawEvent.ResetSession) {
            sessionId = RumContext.NULL_UUID
            resetSessionTime = System.nanoTime()
            applicationDisplayed = false
        }
        updateSessionIdIfNeeded()

        val actualWriter = if (keepSession) writer else noOpWriter

        val iterator = childrenScopes.iterator()
        @Suppress("UnsafeThirdPartyFunctionCall") // next/remove can't fail: we checked hasNext
        while (iterator.hasNext()) {
            val scope = iterator.next().handleEvent(event, actualWriter)
            if (scope == null) {
                iterator.remove()
            }
        }

        if (event is RumRawEvent.StartView) {
            val viewScope = RumViewScope.fromEvent(
                this,
                event,
                firstPartyHostDetector,
                cpuVitalMonitor,
                memoryVitalMonitor,
                frameRateVitalMonitor,
                timeProvider,
                rumEventSourceProvider
            )
            onViewDisplayed(event, viewScope, actualWriter)
            childrenScopes.add(viewScope)
        } else if (childrenScopes.count { it.isActive() } == 0) {
            handleOrphanEvent(event, actualWriter)
        }

        return this
    }

    override fun getRumContext(): RumContext {
        updateSessionIdIfNeeded()
        return if (keepSession) {
            parentScope.getRumContext().copy(sessionId = sessionId)
        } else {
            RumContext()
        }
    }

    override fun isActive(): Boolean {
        return true
    }

    // endregion

    // region Internal

    private fun handleOrphanEvent(
        event: RumRawEvent,
        writer: DataWriter<Any>
    ) {
        val isForegroundProcess =
            CoreFeature.processImportance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        if (applicationDisplayed || !isForegroundProcess) {
            handleBackgroundEvent(event, writer)
        } else {
            handleAppLaunchEvent(event, writer)
        }
    }

    private fun handleAppLaunchEvent(
        event: RumRawEvent,
        actualWriter: DataWriter<Any>
    ) {
        val isValidAppLaunchEvent = event.javaClass in validAppLaunchEventTypes
        val isSilentOrphanEvent = event.javaClass in silentOrphanEventTypes

        if (isValidAppLaunchEvent) {
            val viewScope = createAppLaunchViewScope(event)
            viewScope.handleEvent(event, actualWriter)
            childrenScopes.add(viewScope)
        } else if (!isSilentOrphanEvent) {
            devLogger.w(MESSAGE_MISSING_VIEW)
        }
    }

    private fun handleBackgroundEvent(
        event: RumRawEvent,
        writer: DataWriter<Any>
    ) {
        val isValidBackgroundEvent = event.javaClass in validBackgroundEventTypes
        val isSilentOrphanEvent = event.javaClass in silentOrphanEventTypes

        if (isValidBackgroundEvent && backgroundTrackingEnabled) {
            // there is no active ViewScope to handle this event. We will assume the application
            // is in background and we will create a special ViewScope (background)
            // to handle all the events.
            val viewScope = createBackgroundViewScope(event)
            viewScope.handleEvent(event, writer)
            childrenScopes.add(viewScope)
        } else if (!isSilentOrphanEvent) {
            devLogger.w(MESSAGE_MISSING_VIEW)
        }
    }

    internal fun createBackgroundViewScope(event: RumRawEvent): RumViewScope {
        return RumViewScope(
            this,
            RUM_BACKGROUND_VIEW_URL,
            RUM_BACKGROUND_VIEW_NAME,
            event.eventTime,
            emptyMap(),
            firstPartyHostDetector,
            NoOpVitalMonitor(),
            NoOpVitalMonitor(),
            NoOpVitalMonitor(),
            timeProvider,
            rumEventSourceProvider,
            type = RumViewScope.RumViewType.BACKGROUND
        )
    }

    internal fun createAppLaunchViewScope(event: RumRawEvent): RumViewScope {
        return RumViewScope(
            this,
            RUM_APP_LAUNCH_VIEW_URL,
            RUM_APP_LAUNCH_VIEW_NAME,
            event.eventTime,
            emptyMap(),
            firstPartyHostDetector,
            NoOpVitalMonitor(),
            NoOpVitalMonitor(),
            NoOpVitalMonitor(),
            timeProvider,
            rumEventSourceProvider,
            type = RumViewScope.RumViewType.APPLICATION_LAUNCH
        )
    }

    internal fun onViewDisplayed(
        event: RumRawEvent.StartView,
        viewScope: RumViewScope,
        writer: DataWriter<Any>
    ) {
        if (!applicationDisplayed) {
            applicationDisplayed = true
            val isForegroundProcess =
                CoreFeature.processImportance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            if (isForegroundProcess) {
                val applicationStartTime = resolveStartupTimeNs()
                viewScope.handleEvent(
                    RumRawEvent.ApplicationStarted(event.eventTime, applicationStartTime),
                    writer
                )
            }
        }
    }

    @SuppressLint("NewApi")
    private fun resolveStartupTimeNs(): Long {
        val resetTimeNs = resetSessionTime
        return when {
            resetTimeNs != null -> resetTimeNs
            buildSdkVersionProvider.version() >= Build.VERSION_CODES.N -> {
                val diffMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
                System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(diffMs)
            }
            else -> Datadog.startupTimeNs
        }
    }

    @Synchronized
    private fun updateSessionIdIfNeeded() {
        val nanoTime = System.nanoTime()
        val isNewSession = sessionId == RumContext.NULL_UUID
        val sessionLength = nanoTime - sessionStartNs.get()
        val duration = nanoTime - lastUserInteractionNs.get()
        val isInactiveSession = duration >= sessionInactivityNanos
        val isLongSession = sessionLength >= sessionMaxDurationNanos

        if (isNewSession || isInactiveSession || isLongSession) {
            keepSession = (random.nextFloat() * 100f) < samplingRate
            sessionStartNs.set(nanoTime)
            sessionId = UUID.randomUUID().toString()
            sessionListener?.onSessionStarted(sessionId, !keepSession)
        }

        lastUserInteractionNs.set(nanoTime)
    }

    // endregion

    companion object {

        internal val validBackgroundEventTypes = arrayOf<Class<*>>(
            RumRawEvent.AddError::class.java,
            RumRawEvent.StartAction::class.java,
            RumRawEvent.StartResource::class.java
        )
        internal val validAppLaunchEventTypes = arrayOf<Class<*>>(
            RumRawEvent.AddError::class.java,
            RumRawEvent.AddLongTask::class.java,
            RumRawEvent.StartAction::class.java,
            RumRawEvent.StartResource::class.java
        )

        internal val silentOrphanEventTypes = arrayOf<Class<*>>(
            RumRawEvent.ApplicationStarted::class.java,
            RumRawEvent.KeepAlive::class.java,
            RumRawEvent.ResetSession::class.java,
            RumRawEvent.StopView::class.java,
            RumRawEvent.ActionDropped::class.java,
            RumRawEvent.ActionSent::class.java,
            RumRawEvent.ErrorDropped::class.java,
            RumRawEvent.ErrorSent::class.java,
            RumRawEvent.LongTaskDropped::class.java,
            RumRawEvent.LongTaskSent::class.java,
            RumRawEvent.ResourceDropped::class.java,
            RumRawEvent.ResourceSent::class.java
        )

        internal val DEFAULT_SESSION_INACTIVITY_NS = TimeUnit.MINUTES.toNanos(15)
        internal val DEFAULT_SESSION_MAX_DURATION_NS = TimeUnit.HOURS.toNanos(4)

        internal const val RUM_BACKGROUND_VIEW_URL = "com/datadog/background/view"
        internal const val RUM_BACKGROUND_VIEW_NAME = "Background"

        internal const val RUM_APP_LAUNCH_VIEW_URL = "com/datadog/application-launch/view"
        internal const val RUM_APP_LAUNCH_VIEW_NAME = "ApplicationLaunch"

        internal const val MESSAGE_MISSING_VIEW =
            "A RUM event was detected, but no view is active. " +
                "To track views automatically, try calling the " +
                "Configuration.Builder.useViewTrackingStrategy() method.\n" +
                "You can also track views manually using the RumMonitor.startView() and " +
                "RumMonitor.stopView() methods."
    }
}
